package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.*;
import com.satergo.extra.LinkedHyperlink;
import com.satergo.extra.market.PriceCurrency;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.TXOutputForm;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoId;
import org.ergoplatform.sdk.ErgoToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class HomeCtrl implements WalletTab, Initializable {

	@FXML private Label headTitle;

	@FXML private Parent info;
	@FXML private Label balance, value;

	// Send section
	private List<Integer> candidates;
	@FXML private TabPane outputTabPane;
	private Address change;
	@FXML private Label paymentRequestIndicator;
	@FXML private Button send;
	@FXML private Label nodeSyncNotice;
	private final SimpleBooleanProperty disableTokens = new SimpleBooleanProperty(false);
	// Transaction data
	@FXML private HBox txIdContainer;
	@FXML private LinkedHyperlink txLink;
	@FXML private Button copyTxId;

	@FXML
	public void logout(ActionEvent e) {
		Main.get().getWalletPage().logout();
	}

	@FXML
	public void showSendOptions(ActionEvent e) {
		SatPromptDialog<Pair<ArrayList<Integer>, Address>> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
		dialog.setTitle(Main.lang("settings"));
		dialog.setHeaderText(null);
		SendOptionsCtrl ctrl = new SendOptionsCtrl(Main.get().getWallet().myAddresses.entrySet().stream().map(entry -> {
			return new SendOptionsCtrl.AddressData(entry.getKey(), entry.getValue(), Main.get().getWallet().publicAddress(entry.getKey()));
		}).toList(), candidates, change);
		Parent content = Load.fxmlControllerFactory("/send-options.fxml", ctrl);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
		dialog.setResultConverter(t -> {
			if (t == ButtonType.OK) {
				return new Pair<>(ctrl.candidates.getChildren().stream()
						.map(n -> (ToggleButton) n).filter(ToggleButton::isSelected)
						.map(ToggleButton::getUserData).map(ud -> (int) ud).collect(Collectors.toCollection(ArrayList::new)), ctrl.changeAddress.getValue().value());
			}
			return null;
		});
		dialog.showForResult().ifPresent(v -> {
			candidates = v.getKey();
			change = v.getValue();
		});
	}

	private void updateInfo(Balance bal) {
		if (bal == null) {
			if (Main.get().getWalletPage().priceError.get())
				value.setText(Main.lang("(error)"));
			return;
		}
		DecimalFormat balanceFormat = new DecimalFormat("0.#####");
		balanceFormat.setRoundingMode(RoundingMode.FLOOR);
		balance.setText(balanceFormat.format(ErgoInterface.toFullErg(bal.confirmed())) + " ERG");
		if (!Main.get().getWalletPage().priceError.get()) {
			if (Main.programData().showPrice.get() && Main.get().market.ergValue.get() != null) {
				PriceCurrency currency = Main.programData().priceCurrency.get();
				BigDecimal converted = ErgoInterface.toFullErg(Main.get().getWallet().lastKnownBalance.get().confirmed()).multiply(Main.get().market.ergValue.get());
				value.setText(FormatNumber.currencyExact(converted, currency) + " " + Main.programData().priceCurrency.get().uc());
			}
		} else {
			value.setText(Main.lang("(error)"));
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		headTitle.textProperty().bind(Main.get().getWallet().name);
		Tooltip nameTooltip = new Tooltip();
		nameTooltip.textProperty().bind(Main.get().getWallet().name);
		headTitle.setTooltip(nameTooltip);

		candidates = new ArrayList<>(Main.get().getWallet().myAddresses.keySet());
		change = Main.get().getWallet().publicAddress(candidates.getFirst());
		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> {
			if (change.wasRemoved()) candidates.remove(change.getKey());
			else if (change.wasAdded()) candidates.add(change.getKey());
		});
		disableTokens.set(Main.get().getWallet().lastKnownBalance.get() == null || Main.get().getWallet().lastKnownBalance.get().confirmedTokens().isEmpty());
		Main.get().getWallet().lastKnownBalance.addListener((obs, old, bal) -> {
			disableTokens.set(bal == null || bal.confirmedTokens().isEmpty());
			updateInfo(bal);
		});
		Main.get().market.ergValue.addListener((obs, old, val) -> updateInfo(Main.get().getWallet().lastKnownBalance.get()));
		updateInfo(Main.get().getWallet().lastKnownBalance.get());
		info.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		value.visibleProperty().bind(Main.programData().showPrice);
		if (Main.programData().blockchainNodeKind.get().embedded) {
			Main.node.nodeBlocksLeft.addListener((observable, oldValue, newValue) -> {
				send.setDisable((int) newValue > 150);
				nodeSyncNotice.setVisible((int) newValue > 150);
			});
		}

		outputTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
		outputTabPane.getTabs().add(createOutputTab());
		Insets initialMargin = VBox.getMargin(outputTabPane);
		outputTabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
			if (c.getList().size() > 1) {
				VBox.setMargin(outputTabPane, new Insets(2, initialMargin.getRight(), initialMargin.getBottom(), initialMargin.getLeft()));
				outputTabPane.getStyleClass().remove("hide-header");
			} else {
				VBox.setMargin(outputTabPane, initialMargin);
				outputTabPane.getStyleClass().add("hide-header");
			}
		});
	}

	@FXML
	public void send(ActionEvent event) {
		Utils.createErgoClient().execute(ctx -> {
			UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
			Optional<Long> fee = ((TXOutputForm) outputTabPane.getTabs().getFirst().getContent()).getFee();
			if (fee.isEmpty()) return null;
			long erg = 0;
			HashMap<ErgoId, String> tokenNames = new HashMap<>();
			HashMap<ErgoId, ErgoToken> tokens = new HashMap<>();
			ArrayList<OutBox> outBoxes = new ArrayList<>();
			for (int i = 0; i < outputTabPane.getTabs().size(); i++) {
				Tab tab = outputTabPane.getTabs().get(i);
				TXOutputForm form = (TXOutputForm) tab.getContent();
				OutBox outBox = form.createOutBox(txBuilder, i);
				erg += outBox.getValue();
				for (ErgoToken token : outBox.getTokens()) {
					ErgoToken existing = tokens.get(token.getId());
					if (existing == null) tokens.put(token.getId(), token);
					else tokens.put(token.getId(), new ErgoToken(token.getId(), token.getValue() + existing.getValue()));
				}
				outBoxes.add(outBox);
				tokenNames.putAll(form.tokenNames());
			}
			List<Address> inputAddresses = candidates.stream().map(Main.get().getWallet()::publicAddress).toList();
			long ergFinal = erg;
			new SimpleTask<>(() -> ErgoInterface.createUnsignedTransaction(ctx, inputAddresses, outBoxes, ergFinal, List.copyOf(tokens.values()), fee.get(), change))
					.onSuccess(unsignedTx -> {
						try {
							SignedTransaction signedTx = Main.get().getWallet().key().sign(ctx, unsignedTx, candidates);
							new SimpleTask<>(() -> Main.get().getWallet().transact(signedTx))
									.onSuccess(transactionId -> {
										send.setDisable(false);
										txLink.setText(transactionId);
										txLink.setUri(Utils.explorerTransactionUrl(transactionId));
										copyTxId.setOnAction(ce -> {
											Utils.copyStringToClipboard(transactionId);
											Utils.showTemporaryTooltip(copyTxId, new Tooltip(Main.lang("copied")), Utils.COPIED_TOOLTIP_MS);
										});
										txIdContainer.setVisible(true);
									})
									.onFail(ex -> {
										Utils.alertUnexpectedException(ex);
										send.setDisable(false);
									})
									.newThread();
						} catch (WalletKey.Failure e) {
							send.setDisable(false);
						}
					})
					.onFail(e -> {
						send.setDisable(false);
						Utils.alertTxBuildException(e, ergFinal, tokens.values(), tokenNames::get);
					})
					.newThread();
			return null;
		});
	}

	private Tab createOutputTab(TXOutputForm form) {
		form.addressProperty().addListener((obs, old, val) -> paymentRequestIndicator.setVisible(false));
		form.addChangeListener(() -> txIdContainer.setVisible(false));
		form.disableTokens.bind(disableTokens);
		Tab tab = new Tab("", form);
		IntegerBinding indexOf = Utils.indexBinding(outputTabPane.getTabs(), tab);
		form.showFee.bind(indexOf.isEqualTo(0));
		tab.textProperty().bind(Bindings.createStringBinding(() -> {
			if (indexOf.get() == -1) return "";
			if (!form.addressProperty().get().isBlank())
				return form.addressProperty().get().substring(0, Math.min(form.addressProperty().get().length(), 5));
			return "#" + FormatNumber.integer(indexOf.get() + 1);
		}, form.addressProperty(), indexOf));
		return tab;
	}

	private Tab createOutputTab() {
		return createOutputTab(new TXOutputForm());
	}

	public void addOutput(ActionEvent e) {
		Tab newTab = createOutputTab();
		outputTabPane.getTabs().add(newTab);
		outputTabPane.getSelectionModel().select(newTab);
	}

	@FXML
	public void clearAll(ActionEvent e) {
		outputTabPane.getTabs().setAll(createOutputTab());
	}

	public void insertErgoURI(ErgoURI ergoURI) {
		outputTabPane.getTabs().setAll(createOutputTab(TXOutputForm.forErgoURI(ergoURI)));
		paymentRequestIndicator.setVisible(true);
	}
}
