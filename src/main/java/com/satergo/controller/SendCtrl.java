package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergouri.ErgoURIString;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.Parameters;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class SendCtrl implements Initializable, WalletTab {
	@FXML private Label paymentRequestIndicator;
	@FXML private TextField address, amount, fee;
	@FXML private Button send;

	@FXML private HBox txIdContainer;
	@FXML private Hyperlink txLink;
	@FXML private Button copyTxId;

	public void insertErgoURI(ErgoURIString ergoURI) {
		address.setText(ergoURI.address);
		paymentRequestIndicator.setVisible(true);
		if (ergoURI.amount != null) {
			amount.setText(ergoURI.amount.toPlainString());
		} else amount.setText("");
		fee.setText("");
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		txIdContainer.managedProperty().bind(txIdContainer.visibleProperty());
		paymentRequestIndicator.managedProperty().bind(paymentRequestIndicator.visibleProperty());
		address.textProperty().addListener((obs, o, n) -> {
			paymentRequestIndicator.setVisible(false);
			txIdContainer.setVisible(false);
		});
		amount.textProperty().addListener((obs, o, n) -> txIdContainer.setVisible(false));
		fee.textProperty().addListener((obs, o, n) -> txIdContainer.setVisible(false));
		send.setOnAction(e -> {
			if (address.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("addressRequired"));
			else if (amount.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("amountRequired"));
			else {
				// todo can it be checked any further than network?
				Address recipient = Address.create(address.getText());
				if (recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.MAINNET) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("recipientIsAMainnetAddress"));
					return;
				}
				if (!recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.TESTNET) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("recipientIsATestnetAddress"));
					return;
				}
				BigDecimal amountFullErg;
				try {
					amountFullErg = new BigDecimal(amount.getText());
				} catch (NumberFormatException ex) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("amountInvalid"));
					return;
				}
				long amountNanoErg = ErgoInterface.toNanoErg(amountFullErg);
				// TODO: check for too many decimals leading to not being able to represent in nanoERG (the user-entered amount is invalid)
				BigDecimal feeFullErg = null;
				if (!fee.getText().isBlank()) {
					try {
						feeFullErg = new BigDecimal(fee.getText());
					} catch (NumberFormatException ex) {
						Utils.alert(Alert.AlertType.ERROR, Main.lang("feeInvalid"));
						return;
					}
				}
				long feeNanoErg = feeFullErg == null ? Parameters.MinFee : ErgoInterface.toNanoErg(feeFullErg);
				// TODO: check for too many decimals leading to not being able to represent in nanoERG (the user-entered fee is invalid)
				if (feeNanoErg < Parameters.MinFee) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("feeTooLow").formatted(ErgoInterface.toFullErg(Parameters.MinFee)));
					return;
				}
				String transactionId = Main.get().getWallet().transact(recipient, amountNanoErg, feeNanoErg);
				txLink.setText(transactionId);
				String explorerUrl = "https://" + (Main.programData().nodeNetworkType.get() == NetworkType.MAINNET ? "explorer" : "testnet") + ".ergoplatform.com/en/transactions";
				txLink.setOnAction(te -> Main.get().getHostServices().showDocument(explorerUrl + "/" + transactionId.substring(1, transactionId.length() - 1)));
				copyTxId.setOnAction(ce -> Utils.copyStringToClipboard(transactionId.substring(1, transactionId.length() - 1)));
				txIdContainer.setVisible(true);
			}
		});
	}
}
