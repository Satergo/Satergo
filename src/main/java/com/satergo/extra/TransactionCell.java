package com.satergo.extra;

import com.satergo.FormatNumber;
import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenSummary;
import javafx.animation.*;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.explorer.client.model.AssetInstanceInfo;
import org.ergoplatform.explorer.client.model.InputInfo;
import org.ergoplatform.explorer.client.model.OutputInfo;
import org.ergoplatform.explorer.client.model.TransactionInfo;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.math.BigDecimal;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

public class TransactionCell extends BorderPane implements Initializable {

	// This is not static because the decimal format symbols need to be reselected when the Locale is changed
	private final DecimalFormat FORMAT_TOTAL = new DecimalFormat("+0.000000000;-0.000000000");

	private final TransactionInfo tx;
	private final Set<String> myAddresses;
	@FXML private Label dateTime;
	@FXML private Hyperlink tokens;
	@FXML private Label totalCoins;
	@FXML private Node top;
	@FXML private GridPane bottom;
	@FXML private StackPane arrow;
	@FXML private StackPane bottomContainer;

	private final BooleanProperty expanded = new SimpleBooleanProperty(null, "expanded", false);
	public BooleanProperty expandedProperty() { return expanded; }
	public void setExpanded(boolean b) { expanded.set(b); }
	public boolean isExpanded() { return expanded.get(); }

	private static final Duration TRANSITION_DURATION = new Duration(350.0);
	private static final boolean CACHE_ANIMATION = Boolean.getBoolean("com.sun.javafx.isEmbedded");
	private DoubleProperty transition;
	private void setTransition(double value) { transitionProperty().set(value); }
	private double getTransition() { return transition == null ? 0.0 : transition.get(); }
	private DoubleProperty transitionProperty() {
		if (transition == null) {
			transition = new SimpleDoubleProperty(this, "transition", 0.0) {
				@Override protected void invalidated() {
					requestLayout();
				}
			};
		}
		return transition;
	}

	private final long ergDiff;
	public TransactionCell(TransactionInfo tx, Set<Address> myAddresses) {
		this.tx = tx;
		// Convert my addresses to string to avoid constantly converting the API strings into Address objects
		this.myAddresses = myAddresses.stream().map(Address::toString).collect(Collectors.toUnmodifiableSet());
		Load.thisFxml(this, "/tx-cell.fxml");
		ergDiff = totalReceived(tx, this.myAddresses) - totalSent(tx, this.myAddresses);
		getStyleClass().add(ergDiff >= 0 ? "green" : "red");
		top.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY)
				setExpanded(!isExpanded());
		});
		// context menu
		MenuItem copyTxId = new MenuItem(Main.lang("copyTransactionId"));
		copyTxId.setOnAction(e -> Utils.copyStringToClipboard(tx.getId()));
		ContextMenu context = new ContextMenu(copyTxId);
		top.setOnContextMenuRequested(e -> {
			if (context.isShowing()) context.hide();
			context.show(top, e.getScreenX(), e.getScreenY());
		});
		// expansion
		arrow.rotateProperty().bind(new DoubleBinding() {
			{ bind(transitionProperty()); }

			@Override protected double computeValue() {
				return -90 * (1.0 - getTransition());
			}
		});
		expanded.addListener((observable, oldValue, newValue) -> {
			transitionStartValue = getTransition();
			doAnimationTransition();
		});
		// Create the content on the first time this cell is expanded
		expanded.addListener(new ChangeListener<>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					createContent();
					expanded.removeListener(this);
				}
			}
		});
		clipRect.widthProperty().bind(widthProperty());
		bottomContainer.setClip(clipRect);
	}

	private void createContent() {
		var inputFlow = VirtualFlow.createVertical(FXCollections.observableList(tx.getInputs()), input -> {
			return Cell.wrapNode(createInOut(TransactionInOut.Type.INPUT, input.getAddress(), input.getValue(), input.getAssets()));
		});
		var outputFlow = VirtualFlow.createVertical(FXCollections.observableList(tx.getOutputs()), output -> {
			return Cell.wrapNode(createInOut(TransactionInOut.Type.OUTPUT, output.getAddress(), output.getValue(), output.getAssets()));
		});
		inputFlow.maxHeightProperty().bind(Bindings.when(expandedProperty()).then(Region.USE_COMPUTED_SIZE).otherwise(0));
		outputFlow.maxHeightProperty().bind(Bindings.when(expandedProperty()).then(Region.USE_COMPUTED_SIZE).otherwise(0));
		bottom.add(new VirtualizedScrollPane<>(inputFlow), 0, 0);
		bottom.add(new VirtualizedScrollPane<>(outputFlow), 1, 0);
	}

	private TransactionInOut createInOut(TransactionInOut.Type type, String address, long value, List<AssetInstanceInfo> assets) {
		return new TransactionInOut(type, Address.create(address), FormatNumber.ergExact(ErgoInterface.toFullErg(value)), !assets.isEmpty(), () -> {
			Utils.alert(Alert.AlertType.INFORMATION, assets.stream().map(a -> a.getName() + ": " + ErgoInterface.fullTokenAmount(a.getAmount(), a.getDecimals()).toPlainString()).collect(Collectors.joining("\n")));
		}, myAddresses.contains(address));
	}

	private Timeline timeline;
	private double transitionStartValue;
	private void doAnimationTransition() {
		Duration duration;
		if (timeline != null && (timeline.getStatus() != Animation.Status.STOPPED)) {
			duration = timeline.getCurrentTime();
			timeline.stop();
		} else {
			duration = TRANSITION_DURATION;
		}
		timeline = new Timeline();
		timeline.setCycleCount(1);
		KeyFrame k1, k2;
		if (isExpanded()) {
			k1 = new KeyFrame(
					Duration.ZERO,
					event -> {
						// start expand
						if (CACHE_ANIMATION) bottom.setCache(true);
						bottom.setVisible(true);
					},
					new KeyValue(transitionProperty(), transitionStartValue)
			);
			k2 = new KeyFrame(
					duration,
					event -> {
						// end expand
						if (CACHE_ANIMATION) bottom.setCache(false);
					},
					new KeyValue(transitionProperty(), 1, Interpolator.LINEAR)
			);
		} else {
			k1 = new KeyFrame(
					Duration.ZERO,
					event -> {
						// Start collapse
						if (CACHE_ANIMATION) bottom.setCache(true);
					},
					new KeyValue(transitionProperty(), transitionStartValue)
			);
			k2 = new KeyFrame(
					duration,
					event -> {
						// end collapse
						bottom.setVisible(false);
						if (CACHE_ANIMATION) bottom.setCache(false);
					},
					new KeyValue(transitionProperty(), 0, Interpolator.LINEAR)
			);
		}
		timeline.getKeyFrames().setAll(k1, k2);
		timeline.play();
	}

	private final Rectangle clipRect = new Rectangle();

	@Override
	protected void layoutChildren() {
		double contentHeight = (getHeight() - top.prefHeight(-1)) * getTransition();
		contentHeight = snapSizeY(contentHeight);
		bottomContainer.resize(getWidth(), contentHeight);
		clipRect.setHeight(contentHeight);
		super.layoutChildren();
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		ZonedDateTime time = Instant.ofEpochMilli(tx.getTimestamp()).atZone(ZoneId.systemDefault());
		dateTime.setText(time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)));
		totalCoins.setText(FORMAT_TOTAL.format(ErgoInterface.toFullErg(ergDiff)) + " ERG");
		Map<TokenSummary, Long> tokensSent = totalTokens(tx, TransactionInOut.Type.INPUT, myAddresses);
		Map<TokenSummary, Long> tokensReceived = totalTokens(tx, TransactionInOut.Type.OUTPUT, myAddresses);
		HashMap<TokenSummary, Long> totalTokens = new HashMap<>(tokensReceived);
		tokensSent.forEach((t, a) -> {
			if (totalTokens.containsKey(t)) {
				totalTokens.put(t, totalTokens.get(t) - a);
			} else {
				totalTokens.put(t, -a);
			}
		});
		totalTokens.values().removeIf(amount -> amount == 0L);
		tokens.setVisible(!totalTokens.isEmpty());
		tokens.setOnAction(event -> {
			Utils.alert(Alert.AlertType.INFORMATION, totalTokens.entrySet().stream().map(e -> {
				BigDecimal amount = ErgoInterface.fullTokenAmount(e.getValue(), e.getKey().decimals());
				String name = e.getKey().name().isBlank() ? Main.lang("unnamed_parentheses") : e.getKey().name();
				return name + ": " + (amount.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + amount.toPlainString();
			}).collect(Collectors.joining("\n")));
		});
	}

	// utils

	private static long totalReceived(TransactionInfo tx, Set<String> myAddresses) {
		return tx.getOutputs().stream().filter(info -> myAddresses.contains(info.getAddress())).mapToLong(OutputInfo::getValue).sum();
	}

	private static long totalSent(TransactionInfo tx, Set<String> myAddresses) {
		return tx.getInputs().stream().filter(info -> myAddresses.contains(info.getAddress())).mapToLong(InputInfo::getValue).sum();
	}

	private static Map<TokenSummary, Long> totalTokens(TransactionInfo tx, TransactionInOut.Type dir, Set<String> myAddresses) {
		record TokenSummaryInfo(String id, int decimals, String name) implements TokenSummary {}

		HashMap<TokenSummary, Long> total = new HashMap<>();
		List<?> list = switch (dir) {
			case INPUT -> tx.getInputs();
			case OUTPUT -> tx.getOutputs();
		};
		for (Object ioput : list) {
			if (!myAddresses.contains((dir == TransactionInOut.Type.INPUT ? ((InputInfo) ioput).getAddress() : ((OutputInfo) ioput).getAddress())))
				continue;
			for (AssetInstanceInfo a : (dir == TransactionInOut.Type.INPUT ? ((InputInfo) ioput).getAssets() : ((OutputInfo) ioput).getAssets())) {
				TokenSummary tokenSummary = new TokenSummaryInfo(a.getTokenId(), Objects.requireNonNullElse(a.getDecimals(), 0), a.getName());
				if (total.containsKey(tokenSummary)) {
					total.put(tokenSummary, total.get(tokenSummary) + a.getAmount());
				} else total.put(tokenSummary, a.getAmount());
			}
		}
		return Collections.unmodifiableMap(total);
	}

}
