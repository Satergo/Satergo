package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.WalletKey;
import com.satergo.controller.ledger.ErgoLedgerAppkit;
import com.satergo.extra.LedgerSelector;
import com.satergo.jledger.LedgerDevice;
import com.satergo.jledger.protocol.ergo.ErgoLedgerException;
import com.satergo.jledger.protocol.ergo.ErgoProtocol;
import com.satergo.jledger.transport.hid.HidLedgerDevice;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.ergoplatform.wallet.secrets.ExtendedPublicKey;
import org.hid4java.HidDevice;
import org.hid4java.HidServicesListener;

import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class LedgerSetupCtrl implements SetupPage.WithoutExtra, Initializable {

	private LedgerSelector ledgerSelector;
	private HidServicesListener servicesListener;
	@FXML
	private Parent root;
	@FXML private Label status;
	@FXML private Node found, notFound;

	@FXML private TextField walletName;
	@FXML private PasswordField password;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
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
			}
		};
		ledgerSelector.startListener();
		if (ledgerSelector.getDevice() == null) {
			status.setText(resources.getString("ledger.noDeviceFound"));
		}
	}

	@FXML
	public void continueProcess(ActionEvent e) {
		ledgerSelector.stop();
		LedgerDevice ledgerDevice = new HidLedgerDevice(ledgerSelector.getDevice());
		ErgoLedgerAppkit ergoLedgerAppkit = new ErgoLedgerAppkit(new ErgoProtocol(ledgerDevice));
		ExtendedPublicKey parentExtPubKey;
		status.setText(Main.lang("ledger.pleaseAcceptRequest"));
		while (true) {
			try {
				parentExtPubKey = ergoLedgerAppkit.requestParentExtendedPublicKey();
				break;
			} catch (ErgoLedgerException ex) {
				if (ex.getSW() == 0x6985) {
					status.setText("Rejected");
				}
			}
		}
		Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
		if (path == null) return;
		char[] pass = password.getText().toCharArray();
		WalletKey.Ledger key = WalletKey.Ledger.create(parentExtPubKey, ergoLedgerAppkit, pass);
		Wallet wallet = Wallet.create(path, key, walletName.getText(), pass);
		Main.get().setWallet(wallet);
		Main.get().displayWalletPage();
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
		ledgerSelector.stop();
	}
}
