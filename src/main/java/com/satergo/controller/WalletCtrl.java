package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.ergo.Balance;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergouri.ErgoURIString;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.util.Pair;
import jfxtras.styles.jmetro.Style;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class WalletCtrl implements Initializable {

	private final Timer TIMER = new Timer();
	private TimerTask priceTimerTask, balanceTimerTask;

	public void cancelTimer() {
		priceTimerTask.cancel();
		balanceTimerTask.cancel();
		TIMER.cancel();
	}

	private final String initialTab;

	public WalletCtrl(String initialTab) {
		this.initialTab = initialTab;
	}

	public WalletCtrl() {
		this("account");
	}

	@FXML private BorderPane root;
	@FXML private BorderPane sidebar;
	@FXML private Label headTitle;
	@FXML private ProgressBar networkStatus = new ProgressBar();
	@FXML private Label blocksLeft = new Label();
	@FXML private ToggleGroup group;

	@FXML private HBox priceBox;
	@FXML private Label balance, priceValue, priceCurrency;

	@FXML private ToggleButton myTokens, settings, send, node;

	private final HashMap<String, Pair<Pane, WalletTab>> tabs = new HashMap<>();

	@SuppressWarnings("unchecked")
	public <T extends WalletTab>T getTab(String id) {
		return (T) tabs.get(id);
	}

	private final DecimalFormat format = new DecimalFormat("0");

	public void openSendWithErgoURI(ErgoURIString ergoURI) {
		send.setSelected(true);
		SendCtrl sendTab = (SendCtrl) tabs.get("send").getValue();
		sendTab.insertErgoURI(ergoURI);
	}

	private void updateBalance(Balance totalBalance) {
		Main.get().getWallet().lastKnownBalance.set(totalBalance);
		String formatted = format.format(ErgoInterface.toFullErg(totalBalance.confirmed()));
		if (formatted.equals("0") && totalBalance.confirmed() != 0)
			this.balance.setText("~0");
		else this.balance.setText(formatted);
		myTokens.setDisable(totalBalance.confirmedTokens().isEmpty());
		if (myTokens.isDisable() && group.getSelectedToggle() == myTokens) {
			group.getToggles().get(1).setSelected(true);
		}
	}

	private BigDecimal oneErgValue;

	private void updatePriceValue(BigDecimal oneErgValue) {
		this.oneErgValue = oneErgValue;
		DecimalFormat priceFormat = new DecimalFormat("0");
		priceFormat.setMaximumFractionDigits(Main.programData().priceCurrency.get().displayDecimals);
		priceCurrency.setText(Main.programData().priceCurrency.get().uc());
		priceValue.setText(priceFormat.format(ErgoInterface.toFullErg(Main.get().getWallet().lastKnownBalance.get().confirmed()).multiply(oneErgValue)));
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		format.setMaximumFractionDigits(4);
		format.setRoundingMode(RoundingMode.FLOOR);
		myTokens.textProperty().bind(Bindings.when(myTokens.disabledProperty()).then(Main.lang("noTokens")).otherwise(Main.lang("myTokens")));
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
			if (!Main.node.isRunning()) // a refresh due to language change will not stop the node (see SettingsCtrl), so check if it is running
				Main.node.start();
			networkStatus.progressProperty().bind(Main.node.nodeSyncProgress);
			blocksLeft.textProperty().bind(Bindings.format(Main.lang("blocksLeft_d"), Main.node.nodeBlocksLeft));
		} else {
			networkStatus.setVisible(false);
			blocksLeft.setText(Main.lang("remoteNode") + "\n" + Main.programData().nodeNetworkType.get());
		}
		priceBox.visibleProperty().bind(Main.programData().showPrice);
		priceBox.managedProperty().bind(Main.programData().showPrice);

		sidebar.prefWidthProperty().bind(root.widthProperty().divide(5));
		// require a tab to be opened
		group.selectedToggleProperty().addListener((obsVal, oldVal, newVal) -> {
			if (newVal == null)
				oldVal.setSelected(true);
			else {
				if (oldVal != null) tabs.get(((ToggleButton) oldVal).getId()).getValue().cleanup();
				root.setCenter(tabs.get(((ToggleButton) newVal).getId()).getKey());
			}
		});

		headTitle.textProperty().bind(Bindings.concat("Ergo - ", Main.get().getWallet().name));

		updateBalance(Main.get().getWallet().balance());
		updatePriceValue(Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get()));

		tabs.put("myTokens", Load.fxmlNodeAndController("/my-tokens.fxml"));
		tabs.put("account", Load.fxmlNodeAndController("/account.fxml"));
		tabs.put("send", Load.fxmlNodeAndController("/send.fxml"));
		tabs.put("receive", Load.fxmlNodeAndController("/receive.fxml"));
		tabs.put("transactions", Load.fxmlNodeAndController("/transactions.fxml"));
		tabs.put("settings", Load.fxmlNodeAndController("/settings.fxml"));
		tabs.put("about", Load.fxmlNodeAndController("/about.fxml"));
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
			tabs.put("node", Load.fxmlNodeAndController("/node-overview.fxml"));
		} else {
			((Pane) node.getParent()).getChildren().remove(node);
			group.getToggles().remove(node);
		}

		ImageView settingsImage = new ImageView();
		settingsImage.setPreserveRatio(true);
		settingsImage.setFitWidth(32);
		// explanation: when the settings button is pressed or selected, use the settings-(theme)-theme-selected.png,
		// otherwise, use settings-(theme)-theme.png
		settingsImage.imageProperty().bind(Bindings.when(Bindings.or(settings.pressedProperty(), settings.selectedProperty()))
					.then(Bindings.when(Main.get().themeStyleProperty().isEqualTo(Style.DARK))
						.then(Load.image("/images/settings-dark-theme-selected.png"))
						.otherwise(Load.image("/images/settings-light-theme-selected.png")))
					.otherwise(Bindings.when(Main.get().themeStyleProperty().isEqualTo(Style.DARK))
						.then(Load.image("/images/settings-dark-theme.png"))
						.otherwise(Load.image("/images/settings-light-theme.png"))));
		settings.setGraphic(settingsImage);

		((ToggleButton) root.lookup("#" + initialTab)).setSelected(true);

		Main.programData().priceSource.addListener((observable, oldValue, newValue) -> updatePriceValue(Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get())));
		Main.programData().priceCurrency.addListener((observable, oldValue, newValue) -> updatePriceValue(Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get())));
		priceTimerTask = new TimerTask() {
			@Override
			public void run() {
				BigDecimal oneErgValue = Main.programData().priceSource.get().fetchPrice(Main.programData().priceCurrency.get());
				Platform.runLater(() -> WalletCtrl.this.updatePriceValue(oneErgValue));
			}
		};
		TIMER.scheduleAtFixedRate(priceTimerTask, 50000, 60000);
		balanceTimerTask = new TimerTask() {
			@Override
			public void run() {
				// run in other thread
				Balance totalBalance = Main.get().getWallet().balance();
				Platform.runLater(() -> {
					updateBalance(totalBalance);
					updatePriceValue(oneErgValue);
				});
			}
		};
		TIMER.scheduleAtFixedRate(balanceTimerTask, 60000, 60000);
	}
}
