package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.hw.svault.SVaultComm;
import com.satergo.hw.svault.SVaultFinder;
import com.satergo.hw.svault.SVaultPrompt;
import com.satergo.keystore.SVaultKey;
import com.welie.blessed.BluetoothCommandStatus;
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

public class SVaultSetupCtrl implements SetupPage.WithoutExtra, Initializable {

	private SVaultFinder svaultFinder;
	private SVaultComm svaultComm;

	@FXML private Parent root;
	@FXML private Label status;
	@FXML private Button connect;
	@FXML private Node walletForm;

	@FXML private TextField walletName;
	@FXML private PasswordField password;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		svaultFinder = new SVaultFinder() {
			@Override
			public void discovered(BluetoothPeripheral peripheral) {
				Platform.runLater(() -> {
					connect.setDisable(false);
					status.setText(Main.lang("svault.discoveredDevice").formatted(peripheral.getName()));
				});
			}

			@Override
			public void connected(SVaultComm svaultComm) {
				SVaultSetupCtrl.this.svaultComm = svaultComm;
				Platform.runLater(() -> {
					connect.setDisable(true);
					status.setText(Main.lang("svault.connectedToDevice").formatted(svaultComm.peripheral().getName()));
					walletForm.setVisible(true);
				});
			}

			@Override
			public void disconnected(SVaultComm svaultComm, BluetoothCommandStatus status) {
				if (Main.get().getWalletPage() != null) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("svault.lostConnection"));
					Main.get().getWalletPage().logout();
				}
			}
		};
		svaultFinder.scan();
		status.setText(Main.lang("svault.scanning"));
	}

	@Override
	public Parent content() {
		return root;
	}

	@FXML
	public void connect(ActionEvent e) {
		svaultFinder.connectToDiscovered();
	}

	@FXML
	public void createWallet(ActionEvent e) {
		if (walletName.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("walletNameRequired"));
		else if (password.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("passwordRequired"));
		else {
			SVaultPrompt.ExtPubKey prompt = new SVaultPrompt.ExtPubKey(svaultComm);
			Utils.initDialog(prompt, root.getScene().getWindow());
			prompt.showForResult().ifPresent(parentExtPubKey -> {
				Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), Main.programData().lastWalletDirectory.get(), walletName.getText() + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
				if (path == null) return;
				char[] pass = password.getText().toCharArray();
				SVaultKey key = SVaultKey.create(parentExtPubKey, svaultComm, pass);
				Wallet wallet = Wallet.create(path, key, walletName.getText(), pass);
				Main.get().setWallet(wallet);
				Main.get().displayWalletPage();
			});
		}
	}
}