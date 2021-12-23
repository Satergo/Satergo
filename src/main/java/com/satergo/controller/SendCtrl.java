package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenInfo;
import com.satergo.ergouri.ErgoURIString;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.ergoplatform.appkit.*;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SendCtrl implements Initializable, WalletTab {
	@FXML private Label paymentRequestIndicator;
	@FXML private TextField address, amount, fee;

	@FXML private HBox txIdContainer;
	@FXML private Hyperlink txLink;
	@FXML private Button copyTxId;
	@FXML private VBox tokenList;

	public void insertErgoURI(ErgoURIString ergoURI) {
		address.setText(ergoURI.address);
		paymentRequestIndicator.setVisible(true);
		if (ergoURI.amount != null) {
			amount.setText(ergoURI.amount.toPlainString());
		} else amount.setText("");
		fee.setText("");
	}

	private static class TokenLine extends BorderPane {
		private final TextField amount = new TextField();
		public final TokenInfo tokenInfo;

		public TokenLine(TokenInfo tokenInfo) {
			this.tokenInfo = tokenInfo;
			setLeft(new Label(tokenInfo.name() + " (" + tokenInfo.id().toString().substring(0, 20) + "...)"));
			setAlignment(getLeft(), Pos.CENTER_LEFT);
			amount.setPromptText(Main.lang("amount"));
			amount.textProperty().addListener((observable, oldValue, newValue) -> setIsValidAmount(amountIsValid() || amount.getText().isEmpty()));
			Button remove = new Button(Main.lang("remove"));
			remove.setOnAction(e -> ((Pane) getParent()).getChildren().remove(this));
			HBox right = new HBox(amount, remove);
			right.setAlignment(Pos.CENTER_RIGHT);
			right.setSpacing(8);
			setRight(right);
		}

		public void setIsValidAmount(boolean isValidAmount) {
			if (isValidAmount) amount.getStyleClass().remove("error");
			else {
				if (!amount.getStyleClass().contains("error"))
					amount.getStyleClass().add("error");
			}
		}

		public boolean hasAmount() {
			return !amount.getText().isEmpty();
		}

		public boolean amountIsValid() {
			return Utils.isValidBigDecimal(amount.getText());
		}

		public BigDecimal getAmount() {
			if (!amountIsValid()) throw new IllegalArgumentException();
			return new BigDecimal(amount.getText());
		}
	}

	@FXML private Hyperlink addToken;

	@FXML
	public void addToken(ActionEvent e) {
		ContextMenu contextMenu = new ContextMenu();
		List<TokenInfo> ownedTokens = Main.get().getWallet().lastKnownBalance.get().confirmedTokens().stream().map(t -> new TokenInfo(ErgoId.create(t.id()), t.decimals(), t.name())).sorted().collect(Collectors.toList());
		for (TokenInfo token : ownedTokens) {
			MenuItem menuItem = new MenuItem();
			menuItem.setText(token.name() + " (" + token.id().toString().substring(0, 20) + "...)");
			menuItem.setOnAction(ae -> tokenList.getChildren().add(new TokenLine(token)));
			contextMenu.getItems().add(menuItem);
		}
		contextMenu.show(addToken, Side.BOTTOM, 0, 0);
	}

	@FXML
	public void send(ActionEvent e) {
		if (address.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("addressRequired"));
		else if (amount.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("amountRequired"));
		else {
			// todo check if the user can afford this transaction (erg & tokens)
			// todo can the address be checked any further than network?
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
			if (!ErgoInterface.hasValidNumberOfDecimals(amountFullErg)) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("amountHasTooManyDecimals"));
				return;
			}
			long amountNanoErg = ErgoInterface.toNanoErg(amountFullErg);
			ErgoToken[] tokensToSend = new ErgoToken[tokenList.getChildren().size()];
			for (int i = 0; i < tokenList.getChildren().size(); i++) {
				TokenLine tokenLine = (TokenLine) tokenList.getChildren().get(i);
				if (!tokenLine.hasAmount()) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("token_s_needsAmount").formatted(tokenLine.tokenInfo.name()));
					return;
				}
				if (!tokenLine.amountIsValid()) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("token_s_hasInvalidAmount").formatted(tokenLine.tokenInfo.name()));
					return;
				}
				tokensToSend[i] = new ErgoToken(tokenLine.tokenInfo.id(), ErgoInterface.longTokenAmount(tokenLine.getAmount(), tokenLine.tokenInfo.decimals()));
			}
			BigDecimal feeFullErg = null;
			if (!fee.getText().isBlank()) {
				try {
					feeFullErg = new BigDecimal(fee.getText());
				} catch (NumberFormatException ex) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("feeInvalid"));
					return;
				}
				if (!ErgoInterface.hasValidNumberOfDecimals(feeFullErg)) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("feeHasTooManyDecimals"));
					return;
				}
			}
			long feeNanoErg = feeFullErg == null ? Parameters.MinFee : ErgoInterface.toNanoErg(feeFullErg);
			if (feeNanoErg < Parameters.MinFee) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("feeTooLow").formatted(ErgoInterface.toFullErg(Parameters.MinFee)));
				return;
			}
			String transactionIdQuoted = Main.get().getWallet().transact(recipient, amountNanoErg, feeNanoErg, tokensToSend);
			String transactionId = transactionIdQuoted.substring(1, transactionIdQuoted.length() - 1);
			txLink.setText(transactionId);
			String explorerUrl = "https://" + (Main.programData().nodeNetworkType.get() == NetworkType.MAINNET ? "explorer" : "testnet") + ".ergoplatform.com/en/transactions";
			txLink.setOnAction(te -> Main.get().getHostServices().showDocument(explorerUrl + "/" + transactionId));
			copyTxId.setOnAction(ce -> Utils.copyStringToClipboard(transactionId));
			txIdContainer.setVisible(true);
		}
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
	}
}
