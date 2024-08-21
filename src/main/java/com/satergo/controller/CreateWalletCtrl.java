package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.ToggleSwitch;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.sdk.SecretString;

import java.net.URL;
import java.util.ResourceBundle;

public class CreateWalletCtrl implements SetupPage.WithoutExtra, SetupPage.CustomLeft, Initializable {
	@FXML private Parent root;

	@FXML private Parent enterDetails;
	@FXML private TextField walletName;
	@FXML private PasswordField password, extendedSeedPassphrase;
	@FXML private Button copySeedPhrase;

	@FXML private Parent viewSeed;
	@FXML private TextArea seedPhraseArea;
	@FXML private ToggleSwitch extendWithPassphrase;

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
		Utils.showTemporaryTooltip(copySeedPhrase, new Tooltip(Main.lang("copied")), Utils.COPIED_TOOLTIP_MS);
	}

	@FXML
	public void continueWallet(ActionEvent e) {
		Runnable action = () -> {
			RepeatSeedCtrl ctrl = new RepeatSeedCtrl(
					walletName.getText(),
					SecretString.create(password.getText()),
					Mnemonic.create(SecretString.create(seedPhrase), SecretString.create(extendWithPassphrase.isSelected() ? extendedSeedPassphrase.getText() : "")));
			Load.fxmlControllerFactory("/setup-page/repeat-seed.fxml", ctrl);
			Main.get().displaySetupPage(ctrl);
		};
		// deselect the "extend this seed with passphrase" if it is empty, to make it clear that empty is the same as unused
		if (extendWithPassphrase.isSelected() && extendedSeedPassphrase.getText().isEmpty()) {
			extendWithPassphrase.setSelected(false);
			Utils.fxRunDelayed(action, 260);
		} else action.run();

	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		extendedSeedPassphrase.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				root.requestFocus();
				e.consume();
				extendWithPassphrase.setSelected(false);
			}
		});
		extendWithPassphrase.selectedProperty().addListener((observable, oldValue, selected) -> {
			if (!selected) extendedSeedPassphrase.setText("");
		});
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
