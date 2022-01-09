package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;

import java.io.File;

public class WalletSetupCtrl implements SetupPage.WithLanguage, SetupPage.CustomLeft {

	@FXML private Parent root;

	@FXML
	public void createWallet(MouseEvent e) {
		if (e.getButton() == MouseButton.PRIMARY)
			Main.get().displaySetupPage(Load.<CreateWalletCtrl>fxmlController("/create-wallet.fxml"));
	}

	@FXML
	public void openWalletFile(MouseEvent e) {
		if (e.getButton() != MouseButton.PRIMARY) return;
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(Main.lang("walletFile"));
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Main.lang("wallet"), "*." + Wallet.FILE_EXTENSION));
		File file = fileChooser.showOpenDialog(Main.get().stage());
		if (file == null) return;
		Utils.requestPassword(Main.lang("passwordOf_s").formatted(file.getName()), password -> {
			Main.get().setWallet(Wallet.fromFile(file.toPath(), password));
			Main.get().displayWalletPage();
		});
	}

	@FXML
	public void restoreFromSeed(MouseEvent e) {
		if (e.getButton() == MouseButton.PRIMARY)
			Main.get().displaySetupPage(Load.<RestoreFromSeedCtrl>fxmlController("/restore-wallet-from-seed.fxml"));
	}

	@FXML
	public void returnToBlockchainSetup(ActionEvent e) {
		Main.get().displayTopSetupPage(Load.<BlockchainSetupCtrl>fxmlController("/blockchain-setup.fxml"));
	}

	@Override
	public Parent recreate() {
		return Load.fxml("/wallet-setup.fxml");
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
		Main.get().displayTopSetupPage(Load.<BlockchainSetupCtrl>fxmlController("/blockchain-setup.fxml"));
	}
}