package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.hw.sov.SOVComm;
import com.satergo.hw.sov.SOVFinder;
import com.satergo.hw.sov.SOVPrompt;
import com.satergo.keystore.SVaultKey;
import com.welie.blessed.BluetoothPeripheral;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class SOVWalletSetupCtrl implements SetupPage.WithoutExtra, Initializable {

	private SOVFinder sovFinder;
	private SOVComm sovComm;

	@FXML private Parent root;
	@FXML private Label status;
	@FXML private Button connect;
	@FXML private Node walletForm;

	@FXML private TextField walletName;
	@FXML private PasswordField password;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		sovFinder = new SOVFinder() {
			@Override
			public void discovered(BluetoothPeripheral peripheral) {
				Platform.runLater(() -> {
					connect.setDisable(false);
					status.setText("Discovered device with name " + peripheral.getName());
				});
			}

			@Override
			public void connected(SOVComm sovComm) {
				SOVWalletSetupCtrl.this.sovComm = sovComm;
				Platform.runLater(() -> {
					connect.setDisable(true);
					status.setText("Connected to device with name " + sovComm.peripheral().getName());
					walletForm.setVisible(true);
				});
			}
		};
		sovFinder.scan();
		status.setText("Scanning for devices. Open the app and start the server.");
	}

	@Override
	public Parent content() {
		return root;
	}

	@FXML
	public void connect(ActionEvent e) {
		sovFinder.connectToDiscovered();
	}

	@FXML
	public void createWallet(ActionEvent e) {
		if (walletName.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("walletNameRequired"));
		else if (password.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("passwordRequired"));
		else {
			SOVPrompt.ExtPubKey prompt = new SOVPrompt.ExtPubKey(sovComm);
			Utils.initDialog(prompt, root.getScene().getWindow());
			prompt.showForResult().ifPresent(parentExtPubKey -> {
				Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), Main.programData().lastWalletDirectory.get(), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
				if (path == null) return;
				char[] pass = password.getText().toCharArray();
				SVaultKey key = SVaultKey.create(parentExtPubKey, sovComm, pass);
				Wallet wallet = Wallet.create(path, key, walletName.getText(), pass);
				Main.get().setWallet(wallet);
				Main.get().displayWalletPage();
			});
		}
	}
}
