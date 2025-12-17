package com.satergo.extra;

import com.satergo.FormatNumber;
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
import javafx.scene.input.MouseButton;
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
	@FXML private Label defaultMinimum;
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
		defaultMinimum.setTooltip(new Tooltip(FormatNumber.ergExact(ErgoInterface.toFullErg(Parameters.MinFee)) + " ERG"));
		defaultMinimum.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY)
				fee.setText(FormatNumber.ergExact(ErgoInterface.toFullErg(Parameters.MinFee)));
		});
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

	public static class InputDataException extends Exception {
		public InputDataException(String message) {
			super(message);
		}
	}
	public OutBox createOutBox(UnsignedTransactionBuilder txBuilder, int boxIndex) throws InputDataException {
		if (address.getText().isBlank()) {
			throw new InputDataException(Main.lang("addressRequired"));
		}
		Address recipient;
		try {
			recipient = Address.create(address.getText());
			if (recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.MAINNET) {
				throw new InputDataException(Main.lang("recipientIsAMainnetAddress"));
			}
			if (!recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.TESTNET) {
				throw new InputDataException(Main.lang("recipientIsATestnetAddress"));
			}
		} catch (RuntimeException e) {
			// Invalid address, check if it is a stealth address instead
			try {
				ErgoStealthAddress stealth = new ErgoStealthAddress(address.getText());
				recipient = stealth.generatePaymentAddress(Main.programData().nodeNetworkType.get());
			} catch (Exception ex) {
				throw new InputDataException(Main.lang("invalidAddress"));
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
				throw new InputDataException(Main.lang("amountRequired"));
			} else {
				try {
					amountFullErg = new BigDecimal(amount.getText());
				} catch (NumberFormatException ex) {
					throw new InputDataException(Main.lang("amountInvalid"));
				}
				if (!ErgoInterface.hasValidNumberOfDecimals(amountFullErg)) {
					throw new InputDataException(Main.lang("amountHasTooManyDecimals"));
				}
			}
		}
		ErgoToken[] tokensToSend = new ErgoToken[tokenList.getChildren().size()];
		for (int i = 0; i < tokenList.getChildren().size(); i++) {
			TokenLine tokenLine = (TokenLine) tokenList.getChildren().get(i);
			if (!tokenLine.hasAmount()) {
				throw new InputDataException(Main.lang("token_s_needsAmount").formatted(tokenLine.tokenSummary.name()));
			}
			if (!tokenLine.amountIsValid()) {
				throw new InputDataException(Main.lang("token_s_hasInvalidAmount").formatted(tokenLine.tokenSummary.name()));
			}
			tokensToSend[i] = new ErgoToken(tokenLine.tokenSummary.id(), ErgoInterface.longTokenAmount(tokenLine.getAmount(), tokenLine.tokenSummary.decimals()));
		}
		OutBoxBuilder outBoxBuilder = txBuilder.outBoxBuilder()
				.contract(recipient.toErgoContract());
		if (tokensToSend.length > 0)
			outBoxBuilder.tokens(tokensToSend);
		return dynamicMinimum ? ErgoInterface.buildWithMinimumBoxValue(outBoxBuilder, boxIndex) : outBoxBuilder.value(ErgoInterface.toNanoErg(amountFullErg)).build();
	}

	public long getFee() throws InputDataException {
		if (!showFee.get())
			throw new IllegalStateException("This form does not ask for the fee");
		BigDecimal feeFullErg = null;
		if (!fee.getText().isBlank()) {
			try {
				feeFullErg = new BigDecimal(fee.getText());
			} catch (NumberFormatException ex) {
				throw new InputDataException(Main.lang("feeInvalid"));
			}
			if (!ErgoInterface.hasValidNumberOfDecimals(feeFullErg)) {
				throw new InputDataException(Main.lang("feeHasTooManyDecimals"));
			}
		}
		long feeNanoErg = feeFullErg == null ? Parameters.MinFee : ErgoInterface.toNanoErg(feeFullErg);
		if (feeNanoErg < Parameters.MinFee) {
			throw new InputDataException(Main.lang("feeTooLow").formatted(ErgoInterface.toFullErg(Parameters.MinFee)));
		}
		return feeNanoErg;
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
			throw new RuntimeException(e);
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
