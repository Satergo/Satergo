package com.satergo.controller;

import com.satergo.*;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.hw.ledger.ErgoLedgerAppkit;
import com.satergo.extra.hw.ledger.LedgerPrompt;
import com.satergo.extra.hw.ledger.LedgerSelector;
import com.satergo.jledger.LedgerDevice;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.transport.hid4java.HidLedgerDevice;
import com.satergo.jledger.transport.speculos.EmulatorLedgerDevice;
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

	private LedgerSelector ledgerSelector;
	@FXML
	private Parent root;
	@FXML private Label status;
	@FXML private Node found;

	@FXML private TextField walletName;
	@FXML private PasswordField password;

	private boolean emulator;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		emulator = SystemProperties.ledgerEmulator().isPresent() && SystemProperties.ledgerEmulatorPort().isPresent();
		if (emulator) {
			status.setText("Found emulator");
			found.setVisible(true);
			return;
		}
		ledgerSelector = new LedgerSelector() {
			@Override
			public void deviceFound(HidDevice device) {
				Platform.runLater(() -> {
					if (LedgerDevice.PRODUCT_IDS.contains(device.getProductId())) {
						status.setText("Found a " + getModelName(device.getProductId()) + " device.");
					} else {
						status.setText("Found a Ledger device, the model is unknown.");
					}
					found.setVisible(true);
				});
				stop();
			}
		};
		ledgerSelector.startListener();
		if (ledgerSelector.getDevice() == null) {
			status.setText(resources.getString("ledger.noDeviceFound"));
		}
	}

	@FXML
	public void createWallet(ActionEvent e) {
		if (!emulator) ledgerSelector.stop();
		System.out.println("Instantiating device");
		LedgerDevice ledgerDevice = emulator
				? new EmulatorLedgerDevice(SystemProperties.ledgerEmulator().get(), SystemProperties.ledgerEmulatorPort().get(), LedgerDevice.NANO_S_PRODUCT_ID)
				: new HidLedgerDevice(ledgerSelector.getDevice());
		System.out.println("Instantiated");
		System.out.println("Opening device");
		if (!ledgerDevice.open()) {
			Utils.alert(Alert.AlertType.ERROR, "Failed to open connection to the Ledger device. " + ledgerSelector.getDevice().getLastErrorMessage());
			return;
		}
		System.out.println("Opened");
		System.out.println("Creating kit");
		ErgoProtocol proto = new ErgoProtocol(ledgerDevice);
		System.out.println("proto.getVersion() = " + proto.getVersion());
		ErgoLedgerAppkit ergoLedgerAppkit = new ErgoLedgerAppkit(proto);
		System.out.println("Created");
		status.setText(Main.lang("ledger.pleaseAcceptRequest"));
		LedgerPrompt.ExtPubKey prompt = new LedgerPrompt.ExtPubKey(ergoLedgerAppkit);
		Utils.initDialog(prompt, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
		prompt.showForResult().ifPresent(parentExtPubKey -> {
			Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), Main.programData().lastWalletDirectory.get(), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
			if (path == null) return;
			char[] pass = password.getText().toCharArray();
			WalletKey.Ledger key = WalletKey.Ledger.create(parentExtPubKey, ergoLedgerAppkit, pass);
			Wallet wallet = Wallet.create(path, key, walletName.getText(), pass);
			Main.get().setWallet(wallet);
			Main.get().displayWalletPage();
		});
	}

	@FXML
	public void rescan(ActionEvent e) {
		ledgerSelector.setDevice(null);
		found.setVisible(false);
		// If the previously found device is still connected, it will be found again, but I think if the user decides to rescan they will have disconnected it.
		ledgerSelector.rescanConnected();
	}

	@Override
	public Parent content() {
		return root;
	}

	@Override
	public void cleanup() {
		if (!emulator) ledgerSelector.stop();
	}
}
