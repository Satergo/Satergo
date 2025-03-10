package com.satergo.controller;

import com.satergo.*;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.hw.ledger.ErgoLedgerAppkit;
import com.satergo.hw.ledger.LedgerFinder;
import com.satergo.hw.ledger.LedgerPrompt;
import com.satergo.jledger.LedgerDevice;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.transport.hid4java.Hid4javaLedgerDevice;
import com.satergo.jledger.transport.speculos.SpeculosLedgerDevice;
import com.satergo.keystore.LedgerKey;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.hid4java.HidDevice;

import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class LedgerSetupCtrl implements SetupPage.WithoutExtra, Initializable {

	private LedgerFinder ledgerFinder;
	@FXML
	private Parent root;
	@FXML private Label status;
	@FXML private Node found;

	@FXML private TextField walletName;
	@FXML private PasswordField password;

	private boolean emulator;
	private LedgerKey createdKey;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		emulator = SystemProperties.ledgerEmulator().isPresent() && SystemProperties.ledgerEmulatorPort().isPresent();
		if (emulator) {
			status.setText("Using emulator");
			found.setVisible(true);
			return;
		}
		ledgerFinder = new LedgerFinder() {
			@Override
			public void deviceFound(HidDevice device) {
				Platform.runLater(() -> {
					if (LedgerDevice.PRODUCT_IDS.containsKey(device.getProductId())) {
						status.setText(Main.lang("ledger.found_s_device").formatted(getModelName(device.getProductId())));
					} else {
						status.setText(Main.lang("ledger.foundUnknownModelDevice"));
					}
					found.setVisible(true);
				});
			}
			@Override
			public void deviceDetached(HidDevice hidDevice) {
				if (createdKey != null && Main.get().getWallet() != null && Main.get().getWallet().key() == createdKey) {
					Platform.runLater(() -> {
						WalletCtrl walletPage = Main.get().getWalletPage();
						if (walletPage != null) {
							Utils.alert(Alert.AlertType.ERROR, Main.lang("ledger.lostConnection"));
							walletPage.logout();
						}
					});
				}
			}
		};
		ledgerFinder.startListener();
		if (ledgerFinder.getDevice() == null) {
			status.setText(resources.getString("ledger.noDeviceFound"));
		}
	}

	@FXML
	public void createWallet(ActionEvent e) {
		if (!emulator) ledgerFinder.stopScanning();
		LedgerDevice ledgerDevice = emulator
				? new SpeculosLedgerDevice(SystemProperties.ledgerEmulator().get(), SystemProperties.ledgerEmulatorPort().get(), 0x1011)
				: new Hid4javaLedgerDevice(ledgerFinder.getDevice());
		try {
			ledgerDevice.open();
		} catch (Exception ex) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("ledger.failedToOpenConnection_s").formatted(ex.getMessage()));
			return;
		}
		ErgoProtocol proto = new ErgoProtocol(ledgerDevice);
		ErgoLedgerAppkit ergoLedgerAppkit = new ErgoLedgerAppkit(proto);
		if (!ergoLedgerAppkit.isAppOpen()) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("ledger.openAppFirst"));
			return;
		}
		status.setText(Main.lang("ledger.pleaseAcceptRequest"));
		LedgerPrompt.ExtPubKey prompt = new LedgerPrompt.ExtPubKey(ergoLedgerAppkit);
		Utils.initDialog(prompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
		prompt.showForResult().ifPresent(parentExtPubKey -> {
			Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), Main.programData().lastWalletDirectory.get(), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
			if (path == null) return;
			char[] pass = password.getText().toCharArray();
			LedgerKey key = LedgerKey.create(parentExtPubKey, ergoLedgerAppkit, pass, Wallet.F2_ENCRYPTION);
			Wallet wallet = Wallet.create(path, key, walletName.getText(), pass);
			Main.get().setWallet(wallet);
			Main.get().displayWalletPage();
			createdKey = key;
		});
	}

	@FXML
	public void rescan(ActionEvent e) {
		ledgerFinder.setDevice(null);
		found.setVisible(false);
		// If the previously found device is still connected, it will be found again, but I think if the user decides to rescan they will have disconnected it.
		ledgerFinder.rescanConnected();
	}

	@Override
	public Parent content() {
		return root;
	}

	@Override
	public void cleanup() {
		if (!emulator) ledgerFinder.stopScanning();
	}
}
