package com.satergo.extra;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergo.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoId;
import org.ergoplatform.sdk.ErgoToken;

import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

public class TXOutputForm extends VBox implements Initializable {

	@FXML private Hyperlink addToken;
	@FXML private VBox tokenList;
	@FXML private TextField address, amount, fee;
	private ContextMenu addTokenContextMenu;

	public TXOutputForm() {
		Load.thisFxml(this, "/tx-output-form.fxml");
	}

	private StringProperty addressProperty;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		getStyleClass().add("feeShown");
		fee.visibleProperty().bind(showFee);
		addToken.disableProperty().bind(disableTokens);
		addressProperty = address.textProperty();
		address.textProperty().addListener(obs -> changeListeners.forEach(Runnable::run));
		amount.textProperty().addListener(obs -> changeListeners.forEach(Runnable::run));
		fee.textProperty().addListener(obs -> changeListeners.forEach(Runnable::run));
		tokenList.getChildren().addListener((InvalidationListener) obs -> changeListeners.forEach(Runnable::run));
	}

	@FXML
	public void addToken(ActionEvent e) {
		if (addTokenContextMenu != null && addTokenContextMenu.isShowing())
			addTokenContextMenu.hide();
		addTokenContextMenu = new ContextMenu();
		List<TokenBalance> ownedTokens = Main.get().getWallet().lastKnownBalance.get().confirmedTokens();
		for (TokenBalance token : ownedTokens) {
			if (tokenList.getChildren().stream().anyMatch(t -> ((TokenLine) t).tokenSummary.id().equals(token.id())))
				continue;
			MenuItem menuItem = new MenuItem();
			menuItem.setText(token.name() + " (" + token.id().substring(0, 20) + "...)");
			ImageView icon = new ImageView(Utils.tokenIcon32x32(ErgoId.create(token.id())));
			icon.setFitWidth(32);
			icon.setFitHeight(32);
			menuItem.setGraphic(icon);
			menuItem.setOnAction(ae -> tokenList.getChildren().add(new TokenLine(token)));
			addTokenContextMenu.getItems().add(menuItem);
		}
		addTokenContextMenu.show(addToken, Side.BOTTOM, 0, 0);
	}

	/**
	 * @return null if an error occurred (the error will have been reported to the user)
	 */
	public OutBox createOutBox(UnsignedTransactionBuilder txBuilder, int boxIndex) {
		if (address.getText().isBlank()) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("addressRequired"));
			return null;
		}
		Address recipient;
		try {
			recipient = Address.create(address.getText());
			if (recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.MAINNET) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("recipientIsAMainnetAddress"));
				return null;
			}
			if (!recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.TESTNET) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("recipientIsATestnetAddress"));
				return null;
			}
		} catch (RuntimeException e) {
			// Invalid address, check if it is a stealth address instead
			try {
				ErgoStealthAddress stealth = new ErgoStealthAddress(address.getText());
				recipient = stealth.generatePaymentAddress(Main.programData().nodeNetworkType.get());
			} catch (Exception ex) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("invalidAddress"));
				return null;
			}
		}
		boolean dynamicMinimum;
		BigDecimal amountFullErg;
		if (!tokenList.getChildren().isEmpty() && amount.getText().isBlank()) {
			// Dynamic minimum value of ERG when no ERG is specified but there are tokens specified
			amountFullErg = null;
			dynamicMinimum = true;
		} else {
			dynamicMinimum = false;
			if (amount.getText().isBlank()) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("amountRequired"));
				return null;
			} else {
				try {
					amountFullErg = new BigDecimal(amount.getText());
				} catch (NumberFormatException ex) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("amountInvalid"));
					return null;
				}
				if (!ErgoInterface.hasValidNumberOfDecimals(amountFullErg)) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("amountHasTooManyDecimals"));
					return null;
				}
			}
		}
		ErgoToken[] tokensToSend = new ErgoToken[tokenList.getChildren().size()];
		for (int i = 0; i < tokenList.getChildren().size(); i++) {
			TokenLine tokenLine = (TokenLine) tokenList.getChildren().get(i);
			if (!tokenLine.hasAmount()) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("token_s_needsAmount").formatted(tokenLine.tokenSummary.name()));
				return null;
			}
			if (!tokenLine.amountIsValid()) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("token_s_hasInvalidAmount").formatted(tokenLine.tokenSummary.name()));
				return null;
			}
			tokensToSend[i] = new ErgoToken(tokenLine.tokenSummary.id(), ErgoInterface.longTokenAmount(tokenLine.getAmount(), tokenLine.tokenSummary.decimals()));
		}
		OutBoxBuilder outBoxBuilder = txBuilder.outBoxBuilder()
				.contract(recipient.toErgoContract());
		if (tokensToSend.length > 0)
			outBoxBuilder.tokens(tokensToSend);
		return dynamicMinimum ? ErgoInterface.buildWithMinimumBoxValue(outBoxBuilder, boxIndex) : outBoxBuilder.value(ErgoInterface.toNanoErg(amountFullErg)).build();
	}

	/** @return empty if an error occurred (the user will have been informed) */
	public Optional<Long> getFee() {
		if (!showFee.get())
			throw new IllegalStateException("This form does not ask for the fee");
		BigDecimal feeFullErg = null;
		if (!fee.getText().isBlank()) {
			try {
				feeFullErg = new BigDecimal(fee.getText());
			} catch (NumberFormatException ex) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("feeInvalid"));
				return Optional.empty();
			}
			if (!ErgoInterface.hasValidNumberOfDecimals(feeFullErg)) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("feeHasTooManyDecimals"));
				return Optional.empty();
			}
		}
		long feeNanoErg = feeFullErg == null ? Parameters.MinFee : ErgoInterface.toNanoErg(feeFullErg);
		if (feeNanoErg < Parameters.MinFee) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("feeTooLow").formatted(ErgoInterface.toFullErg(Parameters.MinFee)));
			return Optional.empty();
		}
		return Optional.of(feeNanoErg);
	}

	public static TXOutputForm forErgoURI(ErgoURI ergoURI) {
		TXOutputForm form = new TXOutputForm();
		try {
			form.address.setText(ergoURI.address);
			if (ergoURI.amount != null)
				form.amount.setText(ergoURI.amount.toPlainString());
			ergoURI.tokens.entrySet()
					.parallelStream()
					.map(entry -> new Pair<>(ErgoInterface.getTokenInfo(Main.programData().nodeNetworkType.get(), entry.getKey()), entry.getValue()))
					.sequential()
					.forEachOrdered(entry -> {
						TokenLine tokenLine = new TokenLine(entry.getKey());
						tokenLine.setAmount(entry.getValue());
						form.tokenList.getChildren().add(tokenLine);
					});
		} catch (Exception e) {
			Utils.alertUnexpectedException(e);
		}
		return form;
	}

	public Map<ErgoId, String> tokenNames() {
		HashMap<ErgoId, String> tokenNames = HashMap.newHashMap(tokenList.getChildren().size());
		for (Node node : tokenList.getChildren()) {
			TokenLine tokenLine = (TokenLine) node;
			tokenNames.put(ErgoId.create(tokenLine.tokenSummary.id()), tokenLine.tokenSummary.name());
		}
		return tokenNames;
	}


	/** whether this form is the only form */
	public final SimpleBooleanProperty showFee = new SimpleBooleanProperty(true) {
		@Override
		protected void invalidated() {
			if (get()) getStyleClass().add("feeShown");
			else getStyleClass().remove("feeShown");
		}
	};
	public final SimpleBooleanProperty disableTokens = new SimpleBooleanProperty(false);

	public StringProperty addressProperty() {
		return addressProperty;
	}

	private final ArrayList<Runnable> changeListeners = new ArrayList<>();
	public void addChangeListener(Runnable runnable) {
		changeListeners.add(runnable);
	}

	public static class TokenLine extends BorderPane {
		@FXML private Label name, idTooltipLabel;
		@FXML private Tooltip idTooltip;
		@FXML private TextField amount;

		public final TokenSummary tokenSummary;

		public TokenLine(TokenSummary tokenSummary) {
			Load.thisFxml(this, "/line/send-token.fxml");
			this.tokenSummary = tokenSummary;
			name.setText(tokenSummary.name());
			idTooltip.setText(tokenSummary.id());
			amount.textProperty().addListener((observable, oldValue, newValue) -> setIsValidAmount(amountIsValid() || amount.getText().isEmpty()));
		}

		public void setIsValidAmount(boolean isValidAmount) {
			if (isValidAmount) getStyleClass().remove("error");
			else {
				if (!getStyleClass().contains("error"))
					getStyleClass().add("error");
			}
		}

		public boolean hasAmount() {
			return !amount.getText().isEmpty();
		}

		public boolean amountIsValid() {
			return Utils.isValidBigDecimal(amount.getText());
		}

		public void setAmount(BigDecimal bigDecimal) {
			this.amount.setText(bigDecimal.toPlainString());
		}

		public BigDecimal getAmount() {
			if (!amountIsValid()) throw new IllegalArgumentException();
			return new BigDecimal(amount.getText());
		}

		@FXML
		public void remove() {
			((Pane) getParent()).getChildren().remove(this);
		}

		@FXML
		public void copyId() {
			Utils.copyStringToClipboard(tokenSummary.id());
			Utils.showTemporaryTooltip(idTooltipLabel, new Tooltip(Main.lang("copied")), 400);
		}
	}
}
