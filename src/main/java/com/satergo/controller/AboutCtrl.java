package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.WalletKey;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.LinkedHyperlink;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.Parameters;
import org.ergoplatform.appkit.UnsignedTransaction;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Collections;
import java.util.ResourceBundle;

public class AboutCtrl implements Initializable, WalletTab {
	@FXML private Label version, author, translatedBy, designerLabel;
	@FXML private LinkedHyperlink designerLink;
	private static final Address DONATION_ADDRESS = Address.create("9gMnqf29LPxos2Lk5Lt6SkTmbWYL1d5QFHygbf6zRXDgL4KtAho");

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		version.setText("Satergo v" + Main.VERSION);
		if (Main.get().translations.getEntries().getFirst().equals(Main.get().translations.getEntry())) {
			translatedBy.setVisible(false);
		}
		translatedBy.setText(Main.lang("translatedIntoThisLanguageBy_s").formatted(Main.get().translations.getEntry().credit()));
		// This is to prevent web crawlers from sending spam to the address
		designerLink.setUri(new String(new char[] { 109, 97, 105, 108, 116, 111, 58, 99, 111, 100, 101, 112, 101, 110, 100, 101, 110, 99, 121, 111, 110, 97, 114, 116, 64, 103, 109, 97, 105, 108, 46, 99, 111, 109 }));
		Utils.accessibleLabel(version, author, translatedBy, designerLabel);
	}

	@FXML
	public void donate(ActionEvent e) {
		if (Main.programData().nodeNetworkType.get() != NetworkType.MAINNET) {
			Utils.alert(Alert.AlertType.ERROR, "You can't donate on testnet");
			return;
		}

		SatPromptDialog<BigDecimal> dialog = new SatPromptDialog<>();
		Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
		dialog.setTitle(Main.lang("donate"));
		dialog.setHeaderText(Main.lang("donate"));

		TextField amount = new TextField();

		Label infoLabel = new Label(Main.lang("donationInfo").formatted(DONATION_ADDRESS.toString()));
		infoLabel.setWrapText(true);
		infoLabel.setMaxWidth(Screen.getPrimary().getBounds().getWidth() / 3.5);
		infoLabel.setTextAlignment(TextAlignment.CENTER);

		VBox root = new VBox(
				infoLabel,
				new VBox(
						new Label(Main.lang("amount")),
						amount
				));
		root.setSpacing(10);

		dialog.getDialogPane().setContent(root);
		ButtonType sendDonationType = new ButtonType(Main.lang("sendDonation"), ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelType = new ButtonType(Main.lang("cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().addAll(sendDonationType, cancelType);

		Node sendDonation = dialog.getDialogPane().lookupButton(sendDonationType);
		amount.textProperty().addListener((observable, oldValue, newValue) -> sendDonation.setDisable(!Utils.isValidBigDecimal(newValue)));

		dialog.setResultConverter(type -> {
			if (type == sendDonationType)
				return new BigDecimal(amount.getText());
			return null;
		});

		BigDecimal amountFullErg = dialog.showForResult().orElse(null);
		if (amountFullErg == null) return;
		try {
			Wallet wallet = Main.get().getWallet();
			UnsignedTransaction unsignedTx = ErgoInterface.createUnsignedTransaction(Utils.createErgoClient(),
					wallet.addressStream().toList(),
					DONATION_ADDRESS, ErgoInterface.toNanoErg(amountFullErg), Parameters.MinFee, Main.get().getWallet().publicAddress(0));
			String txId = wallet.transact(Utils.createErgoClient().execute(ctx -> {
				try {
					return wallet.key().sign(ctx, unsignedTx, wallet.myAddresses.keySet());
				} catch (WalletKey.Failure ex) {
					return null;
				}
			}));
			if (txId != null) Utils.textDialogWithCopy(Main.lang("transactionId"), txId);
		} catch (Exception ex) {
			Utils.alertTxBuildException(ex, ErgoInterface.toNanoErg(amountFullErg), Collections.emptyList(), id -> {throw new UnsupportedOperationException();});
		}
	}
}
