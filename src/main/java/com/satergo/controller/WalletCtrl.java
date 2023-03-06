package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.WalletKey;
import com.satergo.ergo.Balance;
import com.satergo.extra.SimpleTask;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.util.Pair;
import org.ergoplatform.appkit.NetworkType;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WalletCtrl implements Initializable {

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0);

	public void cancelRepeatingTasks() {
		scheduler.shutdown();
	}

	private final String initialTab;

	public WalletCtrl(String initialTab) {
		this.initialTab = initialTab;
	}

	public WalletCtrl() {
		this("home");
	}

	@FXML final SimpleBooleanProperty offlineMode = new SimpleBooleanProperty(false);
	@FXML private final BooleanBinding notOfflineMode = offlineMode.not();

	@FXML private BorderPane walletRoot;
	@FXML private Node connectionWarning;
	@FXML private BorderPane sidebar;
	@FXML private Label networkStatusLabel;
	@FXML private ProgressBar networkProgress;
	@FXML private Label thingLeft;
	@FXML private ToggleGroup group;

	@FXML private ToggleButton home, account, transactions, node, settings;

	private final HashMap<String, Pair<Pane, WalletTab>> tabs = new HashMap<>();

	@SuppressWarnings("unchecked")
	public <T extends WalletTab>T getTab(String id) {
		return (T) tabs.get(id).getValue();
	}

	private final DecimalFormat format = new DecimalFormat("0");

	private void updateBalance(Balance totalBalance) {
		Main.get().getWallet().lastKnownBalance.set(totalBalance);
	}

	private void updatePriceValue(BigDecimal oneErgValue) {
		Main.get().lastOneErgValue.set(oneErgValue);
	}

	private void updatePriceValue() {
		try {
			updatePriceValue(Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get()));
		} catch (IOException e) {
			offlineMode();
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		Main.get().setWalletPageInternal(this);
		format.setMaximumFractionDigits(4);
		format.setRoundingMode(RoundingMode.FLOOR);
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
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
			updateBalance(Main.get().getWallet().totalBalance());
			revertOfflineMode();
		} catch (IOException e) {
			offlineMode();
		}
		updatePriceValue();

		tabs.put("home", Load.fxmlNodeAndController("/home.fxml"));
		tabs.put("account", Load.fxmlNodeAndController("/account.fxml"));
		tabs.put("transactions", Load.fxmlNodeAndController("/transactions.fxml"));
		tabs.put("settings", Load.fxmlNodeAndController("/settings.fxml"));
		tabs.put("about", Load.fxmlNodeAndController("/about.fxml"));
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
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
		scheduler.scheduleAtFixedRate(() -> {
			try {
				BigDecimal oneErgValue = Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get());
				Platform.runLater(() -> {
					revertOfflineMode();
					updatePriceValue(oneErgValue);
				});
			} catch (IOException e) {
				Platform.runLater(this::offlineMode);
			}
		}, 50, 60, TimeUnit.SECONDS);
		scheduler.scheduleAtFixedRate(() -> {
			try {
				Balance totalBalance = Main.get().getWallet().totalBalance();
				Platform.runLater(() -> {
					revertOfflineMode();
					updateBalance(totalBalance);
				});
			} catch (ConnectException e) {
				Platform.runLater(this::offlineMode);
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
			updateBalance(v.getKey());
			updatePriceValue(v.getValue());
			revertOfflineMode();
		}).newThread();
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
