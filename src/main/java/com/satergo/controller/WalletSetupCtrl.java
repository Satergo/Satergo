package com.satergo.controller;

import com.satergo.*;
import com.satergo.extra.IncorrectPasswordException;
import com.satergo.keystore.WalletKey;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class WalletSetupCtrl implements Initializable, SetupPage.WithExtra, SetupPage.CustomLeft {

	@FXML private Label nodeConfigurationInfo;
	@FXML private Parent root;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		String langProperty;
		String param2;
		if (Main.programData().blockchainNodeKind.get() == ProgramData.NodeKind.EMBEDDED_FULL_NODE) {
			langProperty = "nodeConfigurationEmbedded_path";
			param2 = Main.node.nodeDirectory.toAbsolutePath().toString();
		} else if (Main.programData().blockchainNodeKind.get() == ProgramData.NodeKind.EMBEDDED_LIGHT_NODE) {
			langProperty = "nodeConfigurationEmbeddedLight_path";
			param2 = Main.node.nodeDirectory.toAbsolutePath().toString();
		} else {
			langProperty = "nodeConfigurationRemote_address";
			param2 = Main.programData().nodeAddress.get();
		}
		nodeConfigurationInfo.setText(Main.lang(langProperty).formatted(Main.programData().nodeNetworkType.get().toString(), param2));
	}

	@FXML
	public void createWallet(ActionEvent e) {
		Main.get().displaySetupPage(Load.<CreateWalletCtrl>fxmlController("/setup-page/create-wallet.fxml"));
	}

	@FXML
	public void openWalletFile(ActionEvent e) {
		FileChooser fileChooser = new FileChooser();
		if (Utils.getLastWalletDir() != null) {
			fileChooser.setInitialDirectory(Utils.getLastWalletDir().toFile());
		}
		fileChooser.setTitle(Main.lang("walletFile"));
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Main.lang("wallet"), "*." + Wallet.FILE_EXTENSION));
		File file = fileChooser.showOpenDialog(Main.get().stage());
		if (file == null) return;
		String password = Utils.requestPassword(Main.lang("passwordOf_s").formatted(file.getName()));
		if (password != null) {
			try {
				Main.programData().lastWalletDirectory.set(file.toPath().getParent());
				Main.get().setWallet(Wallet.load(file.toPath(), password));
				Main.get().displayWalletPage();
			} catch (IncorrectPasswordException ex) {
				Utils.alertIncorrectPassword();
			} catch (WalletKey.WalletOpenException ex) {
				Utils.alert(Alert.AlertType.ERROR, ex.getMessage());
			}
		}
	}

	@FXML
	public void restoreFromSeed(ActionEvent e) {
		Main.get().displaySetupPage(Load.<RestoreFromSeedCtrl>fxmlController("/setup-page/restore-wallet-from-seed.fxml"));
	}

	@FXML
	public void createWalletForHardware(ActionEvent e) {
		Main.get().displaySetupPage(Load.<HardwareWalletSetupCtrl>fxmlController("/setup-page/hardware-wallet.fxml"));
	}

	@Override
	public Parent recreate() {
		return Load.fxml("/setup-page/wallet.fxml");
	}

	@Override
	public Parent content() {
		return root;
	}

	@Override
	public boolean hasLeft() {
		return true;
	}

	@Override
	public void left() {
		Main.get().displayTopSetupPage(Load.<BlockchainSetupCtrl>fxmlController("/setup-page/blockchain.fxml"));
	}
}