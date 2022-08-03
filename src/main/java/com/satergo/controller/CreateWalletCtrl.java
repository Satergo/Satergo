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
import javafx.scene.layout.GridPane;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.SecretString;

import java.net.URL;
import java.util.ResourceBundle;

public class CreateWalletCtrl implements SetupPage.WithoutLanguage, Initializable {
	@FXML private Parent root;
	@FXML private GridPane grid;
	@FXML private TextArea mnemonicPhraseArea;
	@FXML private TextField walletName;
	@FXML private PasswordField password, mnemonicPassword;
	@FXML private Hyperlink addMnemonicPassword;
	@FXML private Button copyMnemonicPhrase, continueWallet;
	@FXML private Label mnemonicPasswordLabel, mnemonicPhraseLabel;

	private String mnemonicPhrase;

	@FXML
	public void initializeWallet(ActionEvent e) {
		if (walletName.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("walletNameRequired"));
		else if (password.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("passwordRequired"));
		else {
			mnemonicPhrase = ErgoInterface.generateMnemonicPhrase("english");
			mnemonicPhraseArea.setText(mnemonicPhrase);
			mnemonicPhraseLabel.setVisible(true);
			walletName.setDisable(true);
			password.setDisable(true);
			addMnemonicPassword.setDisable(true);
			mnemonicPassword.setDisable(true);
		}
	}

	@FXML
	public void copyMnemonicPhrase(ActionEvent e) {
		Utils.copyStringToClipboard(mnemonicPhrase);
	}

	@FXML
	public void continueWallet(ActionEvent e) {
		RepeatMnemonicCtrl ctrl = new RepeatMnemonicCtrl(
				walletName.getText(),
				SecretString.create(password.getText()),
				Mnemonic.create(SecretString.create(mnemonicPhrase), SecretString.create(mnemonicPassword.getText())));
		Load.fxmlControllerFactory("/setup-page/repeat-mnemonic.fxml", ctrl);
		Main.get().displaySetupPage(ctrl);
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		grid.prefWidthProperty().bind(Main.get().stage().widthProperty().multiply(0.8));
		grid.maxWidthProperty().bind(grid.prefWidthProperty());
		mnemonicPhraseLabel.managedProperty().bind(mnemonicPhraseLabel.visibleProperty());
		copyMnemonicPhrase.managedProperty().bind(mnemonicPhraseLabel.managedProperty());
		copyMnemonicPhrase.visibleProperty().bind(mnemonicPhraseLabel.visibleProperty());
		mnemonicPhraseArea.managedProperty().bind(mnemonicPhraseLabel.managedProperty());
		mnemonicPhraseArea.visibleProperty().bind(mnemonicPhraseLabel.visibleProperty());
		continueWallet.managedProperty().bind(mnemonicPhraseLabel.managedProperty());
		continueWallet.visibleProperty().bind(mnemonicPhraseLabel.visibleProperty());
	}

	@FXML
	public void addMnemonicPassword(ActionEvent e) {
		addMnemonicPassword.setVisible(false);
		mnemonicPasswordLabel.setVisible(true);
		mnemonicPassword.setVisible(true);
	}

	@Override
	public Parent content() {
		return root;
	}
}
