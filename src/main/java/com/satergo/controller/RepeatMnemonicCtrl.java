package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.extra.MnemonicPhraseOrderVerify;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.SecretString;

import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class RepeatMnemonicCtrl implements Initializable {
	private final String walletName;
	private final SecretString password;
	private final Mnemonic mnemonic;

	public RepeatMnemonicCtrl(String walletName, SecretString password, Mnemonic mnemonic) {
		this.walletName = walletName;
		this.password = password;
		this.mnemonic = mnemonic;
	}

	private MnemonicPhraseOrderVerify verify;
	@FXML private Label mnemonicProgress, mnemonicPasswordLabel;
	@FXML private Group verifyHolder;
	@FXML private PasswordField mnemonicPassword;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		verify = new MnemonicPhraseOrderVerify(mnemonic.getPhrase().toStringUnsecure().split(" "));
		verifyHolder.getChildren().add(verify);
		verify.onWordAdded = verify.onWordRemoved = word -> mnemonicProgress.setText(String.join(" ", verify.userOrder));
		if (mnemonic.getPassword().isEmpty()) {
			mnemonicPasswordLabel.setVisible(false);
			mnemonicPassword.setVisible(false);
			mnemonicPasswordLabel.setManaged(false);
			mnemonicPassword.setManaged(false);
		}
	}

	@FXML
	public void createWallet(ActionEvent e) {
		if (!verify.isCorrect()) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("incorrectWordOrder"));
		} else if (!SecretString.create(mnemonicPassword.getText()).equals(mnemonic.getPassword())) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("incorrectMnemonicPassword"));
		} else {
			Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("locationToSaveTo"), walletName + "." + Wallet.FILE_EXTENSION, Wallet.extensionFilter());
			if (path == null) return;
			Main.get().setWallet(Wallet.create(path, mnemonic, walletName, password));
			Main.get().displayWalletPage();
		}
	}
}
