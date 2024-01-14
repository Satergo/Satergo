package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.Balance;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenBalance;
import com.satergo.ergo.TokenSummary;
import com.satergo.ergo.ErgoURI;
import com.satergo.extra.PriceCurrency;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.collections.MapChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Pair;
import org.ergoplatform.appkit.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class HomeCtrl implements WalletTab, Initializable {

	@FXML private Label headTitle;

	@FXML private Parent info;
	@FXML private Label balance, value;

	// Send section
	private List<Integer> candidates;
	private Address change;
	@FXML private Label paymentRequestIndicator;
	@FXML private Hyperlink addToken;
	@FXML private VBox tokenList;
	@FXML private TextField sendAddress, sendAmount, sendFee;
	@FXML private Button send;
	@FXML private Label nodeSyncNotice;
	private ContextMenu addTokenContextMenu;
	// Transaction data
	@FXML private HBox txIdContainer;
	@FXML private Hyperlink txLink;
	@FXML private Button copyTxId;

	@FXML
	public void logout(ActionEvent e) {
		Main.get().getWalletPage().logout();
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

	@FXML
	public void showSendOptions(ActionEvent e) {
		SatPromptDialog<Pair<ArrayList<Integer>, Address>> dialog = new SatPromptDialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setMoveStyle(MoveStyle.FOLLOW_OWNER);
		Main.get().applySameTheme(dialog.getScene());
		dialog.setTitle(Main.lang("settings"));
		dialog.setHeaderText(null);
		SendOptionsCtrl ctrl = new SendOptionsCtrl(Main.get().getWallet().myAddresses.entrySet().stream().map(entry -> {
			try {
				return new SendOptionsCtrl.AddressData(entry.getKey(), entry.getValue(), Main.get().getWallet().publicAddress(entry.getKey()));
			} catch (WalletKey.Failure ex) {
				throw new RuntimeException(ex);
			}
		}).toList(), candidates, change);
		Parent content = Load.fxmlControllerFactory("/send-options.fxml", ctrl);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
		dialog.setResultConverter(t -> {
			if (t == ButtonType.OK) {
				return new Pair<>(ctrl.candidates.getChildren().stream()
						.map(n -> (ToggleButton) n).filter(ToggleButton::isSelected)
						.map(ToggleButton::getUserData).map(ud -> (int) ud).collect(Collectors.toCollection(ArrayList::new)), ctrl.changeAddress.getValue().value());
			}
			return null;
		});
		dialog.showForResult().ifPresent(v -> {
			candidates = v.getKey();
			change = v.getValue();
		});
	}

	private void updateInfo(Balance bal) {
		if (bal == null) return;
		DecimalFormat balanceFormat = new DecimalFormat("0.#####");
		balanceFormat.setRoundingMode(RoundingMode.FLOOR);
		balance.setText(balanceFormat.format(ErgoInterface.toFullErg(bal.confirmed())) + " ERG");
		PriceCurrency currency = Main.programData().priceCurrency.get();
		BigDecimal converted = ErgoInterface.toFullErg(Main.get().getWallet().lastKnownBalance.get().confirmed()).multiply(Main.get().lastOneErgValue.get());
		value.setText(FormatNumber.currencyExact(converted, currency) + " " + Main.programData().priceCurrency.get().uc());
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		headTitle.textProperty().bind(Main.get().getWallet().name);
		Tooltip nameTooltip = new Tooltip();
		nameTooltip.textProperty().bind(Main.get().getWallet().name);
		headTitle.setTooltip(nameTooltip);

		candidates = new ArrayList<>(Main.get().getWallet().myAddresses.keySet());
		try {
			change = Main.get().getWallet().publicAddress(candidates.get(0));
		} catch (WalletKey.Failure e) {
			// won't happen because the master address is always either cached or available
		}
		sendAddress.textProperty().addListener((obs, o, n) -> {
			paymentRequestIndicator.setVisible(false);
			txIdContainer.setVisible(false);
		});
		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> {
			if (change.wasRemoved()) candidates.remove(change.getKey());
			else if (change.wasAdded()) candidates.add(change.getKey());
		});
		sendAmount.textProperty().addListener((obs, o, n) -> txIdContainer.setVisible(false));
		sendFee.textProperty().addListener((obs, o, n) -> txIdContainer.setVisible(false));
		addToken.setDisable(Main.get().getWallet().lastKnownBalance.get() == null || Main.get().getWallet().lastKnownBalance.get().confirmedTokens().isEmpty());
		Main.get().getWallet().lastKnownBalance.addListener((obs, old, bal) -> {
			addToken.setDisable(bal == null || bal.confirmedTokens().isEmpty());
			updateInfo(bal);
		});
		Main.get().lastOneErgValue.addListener((obs, old, val) -> updateInfo(Main.get().getWallet().lastKnownBalance.get()));
		updateInfo(Main.get().getWallet().lastKnownBalance.get());
		info.visibleProperty().bind(Main.get().getWalletPage().offlineMode.not());
		if (Main.programData().blockchainNodeKind.get().embedded) {
			Main.node.nodeBlocksLeft.addListener((observable, oldValue, newValue) -> {
				send.setDisable((int) newValue > 150);
				nodeSyncNotice.setVisible((int) newValue > 150);
			});
		}

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

	@FXML
	public void send(ActionEvent e) {
		TextField address = sendAddress, amount = sendAmount, fee = sendFee;

		if (address.getText().isBlank()) Utils.alert(Alert.AlertType.ERROR, Main.lang("addressRequired"));
		else {
			Address recipient;
			try {
				recipient = Address.create(address.getText());
			} catch (RuntimeException ex) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("invalidAddress"));
				return;
			}
			if (recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.MAINNET) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("recipientIsAMainnetAddress"));
				return;
			}
			if (!recipient.isMainnet() && Main.programData().nodeNetworkType.get() != NetworkType.TESTNET) {
				Utils.alert(Alert.AlertType.ERROR, Main.lang("recipientIsATestnetAddress"));
				return;
			}
			BigDecimal amountFullErg;
			if (!tokenList.getChildren().isEmpty() && amount.getText().isBlank()) {
				// Default value of 0.001 ERG when none is specified but there are tokens specified
				amountFullErg = new BigDecimal("0.001");
			} else {
				if (amount.getText().isBlank()) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("amountRequired"));
					return;
				} else {
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
				}
			}
			long amountNanoErg = ErgoInterface.toNanoErg(amountFullErg);
			ErgoToken[] tokensToSend = new ErgoToken[tokenList.getChildren().size()];
			HashMap<ErgoId, String> tokenNames = new HashMap<>(tokenList.getChildren().size());
			for (int i = 0; i < tokenList.getChildren().size(); i++) {
				TokenLine tokenLine = (TokenLine) tokenList.getChildren().get(i);
				if (!tokenLine.hasAmount()) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("token_s_needsAmount").formatted(tokenLine.tokenSummary.name()));
					return;
				}
				if (!tokenLine.amountIsValid()) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("token_s_hasInvalidAmount").formatted(tokenLine.tokenSummary.name()));
					return;
				}
				tokensToSend[i] = new ErgoToken(tokenLine.tokenSummary.id(), ErgoInterface.longTokenAmount(tokenLine.getAmount(), tokenLine.tokenSummary.decimals()));
				tokenNames.put(ErgoId.create(tokenLine.tokenSummary.id()), tokenLine.tokenSummary.name());
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
			Wallet wallet = Main.get().getWallet();
			send.setDisable(true);
			Task<UnsignedTransaction> unsignedTxTask = new Task<>() {
				@Override
				protected UnsignedTransaction call() {
					return ErgoInterface.createUnsignedTransaction(Utils.createErgoClient(),
							candidates.stream().map(index -> {
								try {
									return Main.get().getWallet().publicAddress(index);
								} catch (WalletKey.Failure ex) {
									throw new RuntimeException(ex);
								}
							}).toList(),
							recipient, amountNanoErg, feeNanoErg, change, tokensToSend);
				}
			};
			unsignedTxTask.setOnSucceeded(s -> {
				UnsignedTransaction unsignedTx = unsignedTxTask.getValue();
				SignedTransaction signedTx = Utils.createErgoClient().execute(ctx -> {
					try {
						return wallet.key().sign(ctx, unsignedTx, candidates);
					} catch (WalletKey.Failure ex) {
						return null;
					}
				});
				if (signedTx == null) {
					send.setDisable(false);
					return;
				}
				Task<String> transactTask = new Task<>() {
					@Override
					protected String call() {
						return wallet.transact(signedTx);
					}
				};
				transactTask.setOnSucceeded(ts -> {
					send.setDisable(false);
					String transactionId = transactTask.getValue();
					txLink.setText(transactionId);
					txLink.setOnAction(te -> Main.get().getHostServices().showDocument(Utils.explorerTransactionUrl(transactionId)));
					copyTxId.setOnAction(ce -> {
						Utils.copyStringToClipboard(transactionId);
						Utils.showTemporaryTooltip(copyTxId, new Tooltip(Main.lang("copied")), 400);
					});
					txIdContainer.setVisible(true);
				});
				transactTask.setOnFailed(te -> {
					// Not sure if it can be null
					if (transactTask.getException() != null) {
						Utils.alertException(Main.lang("unexpectedError"), Main.lang("anUnexpectedErrorOccurred"), transactTask.getException());
						transactTask.getException().printStackTrace();
					}
					send.setDisable(false);
				});
				new Thread(transactTask).start();
			});
			unsignedTxTask.setOnFailed(f -> {
				send.setDisable(false);
				if (unsignedTxTask.getException() instanceof InputBoxesSelectionException.NotEnoughErgsException ex) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("youDoNotHaveEnoughErg_s_moreNeeded").formatted(FormatNumber.ergExact(ErgoInterface.toFullErg(amountNanoErg - ex.balanceFound))));
				} else if (unsignedTxTask.getException() instanceof InputBoxesSelectionException.NotEnoughTokensException ex) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("youDoNotHaveEnoughOf_s").formatted(ex.tokenBalances.keySet().stream().map(ErgoId::create).map(tokenNames::get).map(name -> '"' + name + '"').collect(Collectors.joining(", "))));
				} else if (unsignedTxTask.getException() != null) {
					Utils.alertException(Main.lang("unexpectedError"), Main.lang("anUnexpectedErrorOccurred"), unsignedTxTask.getException());
					unsignedTxTask.getException().printStackTrace();
				}
			});
			new Thread(unsignedTxTask).start();
		}
	}

	@FXML
	public void clearAll(ActionEvent e) {
		sendAddress.setText("");
		sendAmount.setText("");
		sendFee.setText("");
		tokenList.getChildren().clear();
	}

	public void insertErgoURI(ErgoURI ergoURI) {
		try {
			clearAll(null);
			sendAddress.setText(ergoURI.address);
			paymentRequestIndicator.setVisible(true);
			if (ergoURI.amount != null)
				sendAmount.setText(ergoURI.amount.toPlainString());
			ergoURI.tokens.entrySet()
					.parallelStream()
					.map(entry -> new Pair<>(ErgoInterface.getTokenInfo(Main.programData().nodeNetworkType.get(), entry.getKey()), entry.getValue()))
					.sequential()
					.forEachOrdered(entry -> {
						TokenLine tokenLine = new TokenLine(entry.getKey());
						tokenLine.setAmount(entry.getValue());
						tokenList.getChildren().add(tokenLine);
					});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
