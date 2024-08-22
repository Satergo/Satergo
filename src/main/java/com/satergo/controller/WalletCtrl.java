package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.Balance;
import com.satergo.ergopay.ErgoPay;
import com.satergo.ergopay.ErgoPayPrompt;
import com.satergo.ergopay.ErgoPayURI;
import com.satergo.ergo.ErgoURI;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.util.Pair;
import javafx.util.StringConverter;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.SignedTransaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WalletCtrl implements Initializable {

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0);

	public void cancelRepeatingTasks() {
		scheduler.shutdownNow();
	}

	private ChangeListener<Boolean> windowFocusListener;
	private final String initialTab;

	public WalletCtrl(String initialTab) {
		this.initialTab = initialTab;
	}

	public WalletCtrl() {
		this("home");
	}

	@FXML final SimpleBooleanProperty offlineMode = new SimpleBooleanProperty(false);
	@FXML private final BooleanBinding notOfflineMode = offlineMode.not();
	private ErgoPayURI lastErgoPayURI;

	@FXML private BorderPane walletRoot;
	@FXML private Node connectionWarning;
	@FXML private Node ergoPayNotice;
	@FXML private ButtonBar ergoPayButtonBar;

	@FXML private BorderPane sidebar;
	@FXML private Label networkStatusLabel;
	@FXML private ProgressBar networkProgress;
	@FXML private Label thingLeft;
	@FXML private ToggleGroup group;

	@FXML private ToggleButton home, account, transactions, node, settings;

	final SimpleBooleanProperty priceError = new SimpleBooleanProperty(false);

	private final HashMap<String, Pair<Pane, WalletTab>> tabs = new HashMap<>();

	@SuppressWarnings("unchecked")
	public <T extends WalletTab>T getTab(String id) {
		return (T) tabs.get(id).getValue();
	}

	private final DecimalFormat format = new DecimalFormat("0");

	private void setBalance(Balance totalBalance) {
		Main.get().getWallet().lastKnownBalance.set(totalBalance);
	}

	private void setPrice(BigDecimal oneErgValue) {
		Main.get().market.ergValue.set(oneErgValue);
	}

	// This method is called from one place on the FX application thread
	// The other times it is called from another thread which is why runLaterOrNow is used.
	private void updatePriceValue() {
		if (!Main.programData().showPrice.get()) {
			setPrice(null);
			return;
		}
		try {
			BigDecimal price = Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get());
			try {
				Main.get().market.updateTokenPrices();
			} catch (IOException | InterruptedException ignored) {
			}
			Utils.runLaterOrNow(() -> {
				revertOfflineMode();
				priceError.set(false);
				setPrice(price);
			});
		} catch (IOException e) {
			Utils.runLaterOrNow(this::offlineMode);
		} catch (Exception e) {
			Utils.runLaterOrNow(() -> {
				priceError.set(true);
				setPrice(null);
			});
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		Main.get().setWalletPageInternal(this);
		format.setMaximumFractionDigits(4);
		format.setRoundingMode(RoundingMode.FLOOR);
		if (Main.programData().blockchainNodeKind.get().embedded) {
			if (!Main.node.isRunning()) // a refresh due to language change will not stop the node (see SettingsCtrl), so check if it is running
				Main.node.start();
			bindToNodeProperties();
		} else {
			networkStatusLabel.setVisible(false);
			networkProgress.setVisible(false);
			thingLeft.setText(Main.lang("remoteNode") + (Main.programData().nodeNetworkType.get() != NetworkType.MAINNET ? "\n" + Main.programData().nodeNetworkType.get() : ""));
		}

		// require a tab to be opened
		group.selectedToggleProperty().addListener((obsVal, oldVal, newVal) -> {
			if (newVal == null)
				oldVal.setSelected(true);
			else {
				if (oldVal != null) tabs.get(((ToggleButton) oldVal).getId()).getValue().cleanup();
				walletRoot.setCenter(tabs.get(((ToggleButton) newVal).getId()).getKey());
			}
		});

		try {
			setBalance(Main.get().getWallet().totalBalance());
			revertOfflineMode();
		} catch (IOException e) {
			offlineMode();
		}
		Main.programData().showPrice.subscribe(show -> updatePriceValue());

		tabs.put("home", Load.fxmlNodeAndController("/home.fxml"));
		tabs.put("account", Load.fxmlNodeAndController("/account.fxml"));
		tabs.put("transactions", Load.fxmlNodeAndController("/transactions.fxml"));
		tabs.put("settings", Load.fxmlNodeAndController("/settings.fxml"));
		tabs.put("about", Load.fxmlNodeAndController("/about.fxml"));
		if (Main.programData().blockchainNodeKind.get().embedded) {
			tabs.put("node", Load.fxmlNodeAndController("/node-overview.fxml"));
		} else {
			((Pane) node.getParent()).getChildren().remove(node);
			group.getToggles().remove(node);
		}
		// I don't know how to bind to controller properties from FXML, it does not work
		connectionWarning.visibleProperty().bind(offlineMode);
		home.disableProperty().bind(offlineMode);
		transactions.disableProperty().bind(offlineMode);

		((ToggleButton) walletRoot.lookup("#" + initialTab)).setSelected(true);

		Main.programData().priceSource.addListener((observable, oldValue, newValue) -> updatePriceValue());
		Main.programData().priceCurrency.addListener((observable, oldValue, newValue) -> {
			// Happens when the available currencies are changed. This listener gets called before the currency is selected.
			if (newValue != null) updatePriceValue();
		});
		scheduler.scheduleAtFixedRate(this::updatePriceValue, 50, 60, TimeUnit.SECONDS);
		scheduler.scheduleAtFixedRate(() -> {
			try {
				Balance totalBalance = Main.get().getWallet().totalBalance();
				Platform.runLater(() -> {
					// This seems very unlikely but it appears to have happened
					if (Main.get().getWallet() != null)
						return;
					revertOfflineMode();
					setBalance(totalBalance);
				});
			} catch (ConnectException e) {
				Platform.runLater(this::offlineMode);
			} catch (Exception e) {
				Utils.alertUnexpectedException(e);
			}
		}, 60, 60, TimeUnit.SECONDS);

		if (Main.get().getWallet().key() instanceof WalletKey.Local) {
			Main.programData().requirePasswordForSending.addListener((observable, oldValue, newValue) -> {
				WalletKey.Local key = (WalletKey.Local) Main.get().getWallet().key();
				if (!oldValue && newValue) {
					key.setCaching(WalletKey.Local.Caching.TIMED);
				} else if (!newValue) {
					key.setCaching(WalletKey.Local.Caching.PERMANENT);
				}
			});
		}

		Button show = new Button(ButtonType.YES.getText()), doNotShow = new Button(ButtonType.NO.getText());
		ButtonBar.setButtonData(show, ButtonType.YES.getButtonData());
		ButtonBar.setButtonData(doNotShow, ButtonType.NO.getButtonData());
		ergoPayButtonBar.getButtons().addAll(show, doNotShow);
		show.setOnAction(event -> {
			ergoPayNotice.setVisible(false);
			handleErgoPayURI(lastErgoPayURI);
		});
		doNotShow.setOnAction(event -> ergoPayNotice.setVisible(false));
		if (SystemProperties.packageType() == SystemProperties.PackageType.PORTABLE) walletRoot.sceneProperty().addListener((obs, old, scene) -> {
			if (scene != null) {
				scene.getWindow().focusedProperty().addListener(windowFocusListener = (observable, oldValue, focused) -> {
					if (focused) {
						String content = Clipboard.getSystemClipboard().getString();
						ErgoPayURI uri;
						try {
							uri = new ErgoPayURI(content);
						} catch (Exception e) {
							return;
						}
						if (uri.equals(lastErgoPayURI))
							return;
						lastErgoPayURI = uri;
						ergoPayNotice.setVisible(true);
					}
				});
			} else {
				old.getWindow().focusedProperty().removeListener(windowFocusListener);
			}
		});
	}

	public void bindToNodeProperties() {
		networkProgress.progressProperty().bind(Bindings.when(Main.node.headersSynced).then(Main.node.nodeSyncProgress).otherwise(Main.node.nodeHeaderSyncProgress));
		thingLeft.textProperty().bind(Bindings.when(Main.node.headersSynced)
				.then(Bindings.format(Main.lang("blocksLeft_s"),
						Bindings.when(Main.node.nodeBlocksLeft.lessThan(0)).then("?").otherwise(Main.node.nodeBlocksLeft.asString())))
				.otherwise(Bindings.format(Main.lang("syncingHeaders_s"),
						Bindings.when(Main.node.nodeHeaderSyncProgress.lessThan(0)).then("?").otherwise(Bindings.createStringBinding(() -> {
							if (Main.node.nodeHeaderHeight.get() <= 0) return "?";
							DecimalFormat df = new DecimalFormat("0.##");
							return df.format(Main.node.nodeHeaderSyncProgress.get() * 100);
						}, Main.node.nodeHeaderSyncProgress)))));
	}

	public void offlineMode() {
		offlineMode.set(true);
		if (home.isSelected() || transactions.isSelected())
			account.setSelected(true);
	}

	public void revertOfflineMode() {
		if (!offlineMode.get()) return;
		offlineMode.set(false);
	}

	@FXML
	public void checkConnection(MouseEvent e) {
		if (e.getButton() != MouseButton.PRIMARY) return;
		new SimpleTask<>(() -> {
			Balance totalBalance = Main.get().getWallet().totalBalance();
			BigDecimal oneErgValue = Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get());
			return new Pair<>(totalBalance, oneErgValue);
		}).onSuccess(v -> {
			setBalance(v.getKey());
			setPrice(v.getValue());
			revertOfflineMode();
		}).newThread();
	}

	public void openErgoURI(ErgoURI ergoURI) {
		home.setSelected(true);
		HomeCtrl homeTab = (HomeCtrl) tabs.get("home").getValue();
		homeTab.insertErgoURI(ergoURI);
	}

	public void handleErgoPayURI(ErgoPayURI uri) {
		Address address;
		if (uri.needsAddress()) {
			SatPromptDialog<Integer> addressPrompt = new SatPromptDialog<>();
			Utils.initDialog(addressPrompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
			addressPrompt.setHeaderText(Main.lang("ergoPay.selectAnAddressToProvide"));
			ComboBox<Integer> comboBox = new ComboBox<>();
			comboBox.getItems().addAll(Main.get().getWallet().myAddresses.keySet());
			comboBox.setValue(0);
			comboBox.setConverter(new StringConverter<>() {
				@Override
				public String toString(Integer object) {
					return Main.get().getWallet().myAddresses.get(object);
				}
				@Override public Integer fromString(String string) { return null; }
			});
			HBox center = new HBox(comboBox);
			center.setAlignment(Pos.CENTER);
			addressPrompt.getDialogPane().setContent(center);
			addressPrompt.setResultConverter(param -> param == ButtonType.OK ? comboBox.getValue() : null);
			addressPrompt.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			Integer index = addressPrompt.showForResult().orElse(null);
			if (index == null) return;
			address = Main.get().getWallet().publicAddress(index);
		} else {
			address = null;
		}
		Callable<ErgoPay.Request> makeRequest = () -> ErgoPay.getRequest(uri, () -> address);
		Consumer<ErgoPay.Request> successHandler = request -> {
			if (request.reducedTx() == null) {
				// TODO show a message?
				return;
			}
			ErgoPayPrompt prompt = new ErgoPayPrompt(request);
			Utils.initDialog(prompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
			if (prompt.showForResult().orElse(false)) {
				String id = Utils.createErgoClient().execute(ctx -> {
					SignedTransaction transaction;
					try {
						transaction = Main.get().getWallet().key().signReduced(ctx, request.reducedTx(), 0, Main.get().getWallet().myAddresses.keySet());
					} catch (WalletKey.Failure e) {
						throw new RuntimeException(e);
					}
					String quoted = ctx.sendTransaction(transaction);
					return quoted.substring(1, quoted.length() - 1);
				});
				SatVoidDialog result = new SatVoidDialog();
				Utils.initDialog(result, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
				result.setHeaderText("The transaction succeeded");
				ButtonType copyId = new ButtonType(Main.lang("ergoPay.copyId"), ButtonBar.ButtonData.OK_DONE);
				ButtonType viewOnExplorer = new ButtonType(Main.lang("ergoPay.viewOnExplorer"), ButtonBar.ButtonData.OK_DONE);
				result.getDialogPane().getButtonTypes().addAll(copyId, viewOnExplorer);
				result.showForResult().ifPresent(t -> {
					if (t == copyId) Utils.copyStringToClipboard(id);
					else if (t == viewOnExplorer) {
						Utils.showDocument(Utils.explorerTransactionUrl(id));
					}
				});
			}
		};
		if (uri.needsNetworkRequest()) {
			new SimpleTask<>(makeRequest)
					.onSuccess(successHandler)
					.onFail(Utils::alertUnexpectedException)
					.newThread();
		} else {
			try {
				successHandler.accept(makeRequest.call());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void logout() {
		cancelRepeatingTasks();
		Main.get().setWallet(null);
		Main.get().displayTopSetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
		if (Main.programData().blockchainNodeKind.get().embedded)
			Main.node.stop();
	}

//	@FXML
//	public void showChart(ActionEvent e) {
//		Dialog<Void> dialog = new Dialog<>();
//		dialog.initOwner(Main.get().stage());
//		dialog.setTitle("Ergo ERG/" + Main.programData().priceCurrency.get().uc());
//		new SimpleTask<>(() -> new ChartView(Main.programData().priceCurrency.get()))
//				.onRunning(() -> priceCurrency.setDisable(true))
//				.onSuccess(v -> {
//					priceCurrency.setDisable(false);
//					dialog.getDialogPane().setContent(v);
//					dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
//					dialog.show();
//				}).newThread();
//	}
}
