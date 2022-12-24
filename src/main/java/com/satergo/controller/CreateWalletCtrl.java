package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.ErgoInterface;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.SecretString;

import java.net.URL;
import java.util.ResourceBundle;

public class CreateWalletCtrl implements SetupPage.WithoutExtra, SetupPage.CustomLeft, Initializable {
	@FXML private Parent root;

	@FXML private Parent enterDetails;
	@FXML private TextField walletName;
	@FXML private PasswordField password, mnemonicPassword;
	@FXML private Button addMnemonicPassword;
	@FXML private Button copySeedPhrase, continueWallet;
	@FXML private VBox mnemonicPasswordBox;
	@FXML private Label seedPhraseLabel;

	@FXML private Parent viewSeed;
	@FXML private TextArea seedPhraseArea;

	private String seedPhrase;

	@FXML
	public void initializeWallet(ActionEvent e) {
		if (walletName.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("walletNameRequired"));
		else if (password.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("passwordRequired"));
		else {
			seedPhrase = ErgoInterface.generateMnemonicPhrase("english");
			seedPhraseArea.setText(seedPhrase);
			enterDetails.setVisible(false);
			viewSeed.setVisible(true);
		}
	}

	@FXML
	public void copySeedPhrase(ActionEvent e) {
		Utils.copyStringToClipboard(seedPhrase);
		Utils.showTemporaryTooltip(copySeedPhrase, new Tooltip(Main.lang("copied")), 400);
	}

	@FXML
	public void continueWallet(ActionEvent e) {
		RepeatSeedCtrl ctrl = new RepeatSeedCtrl(
				walletName.getText(),
				SecretString.create(password.getText()),
				Mnemonic.create(SecretString.create(seedPhrase), SecretString.create(mnemonicPassword.getText())));
		Load.fxmlControllerFactory("/setup-page/repeat-seed.fxml", ctrl);
		Main.get().displaySetupPage(ctrl);
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		mnemonicPassword.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				root.requestFocus();
				e.consume();
			}
		});
		mnemonicPassword.focusedProperty().addListener((observable, oldValue, focused) -> {
			if (!focused && mnemonicPassword.getText().isEmpty()) {
				addMnemonicPassword.setVisible(true);
				mnemonicPasswordBox.setVisible(false);
			}
		});
	}

	@FXML
	public void addMnemonicPassword(ActionEvent e) {
		addMnemonicPassword.setVisible(false);
		mnemonicPasswordBox.setVisible(true);
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
		if (!enterDetails.isVisible()) {
			seedPhrase = null;
			seedPhraseArea.clear();
			enterDetails.setVisible(true);
			viewSeed.setVisible(false);
		} else Main.get().previousPage();
	}
}
