package com.satergo.controller;

import com.satergo.*;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenBalance;
import com.satergo.ergouri.ErgoURIString;
import javafx.collections.MapChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.*;
import org.ergoplatform.appkit.InputBoxesSelectionException.NotEnoughErgsException;
import org.ergoplatform.appkit.InputBoxesSelectionException.NotEnoughTokensException;

import java.math.BigDecimal;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SendCtrl implements Initializable, WalletTab {
	@FXML private Label paymentRequestIndicator;
	@FXML private TextField address, amount, fee;

	@FXML private Button send;
	@FXML private Label nodeSyncNotice;
	@FXML private HBox txIdContainer;
	@FXML private Hyperlink txLink;
	@FXML private Button copyTxId;
	@FXML private VBox tokenList;

	private List<Integer> candidates;
	private Address change;

	public void insertErgoURI(ErgoURIString ergoURI) {
		address.setText(ergoURI.address);
		paymentRequestIndicator.setVisible(true);
		if (ergoURI.amount != null) {
			amount.setText(ergoURI.amount.toPlainString());
		} else amount.setText("");
		fee.setText("");
	}

	private static class TokenLine extends BorderPane {
		@FXML private Label name;
		@FXML private TextField amount;

		public final TokenBalance tokenInfo;

		public TokenLine(TokenBalance tokenInfo) {
			Load.thisFxml(this, "/send-token-line.fxml");
			this.tokenInfo = tokenInfo;
			name.setText(tokenInfo.name() + " (" + tokenInfo.id().substring(0, 20) + "...)");
			amount.textProperty().addListener((observable, oldValue, newValue) -> setIsValidAmount(amountIsValid() || amount.getText().isEmpty()));
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

		@FXML
		public void remove() {
			((Pane) getParent()).getChildren().remove(this);
		}
	}

	@FXML private Hyperlink addToken;

	@FXML
	public void addToken(ActionEvent e) {
		ContextMenu contextMenu = new ContextMenu();
		List<TokenBalance> ownedTokens = Main.get().getWallet().lastKnownBalance.get().confirmedTokens();
		for (TokenBalance token : ownedTokens) {
			if (tokenList.getChildren().stream().anyMatch(t -> ((TokenLine) t).tokenInfo.id().equals(token.id())))
				continue;
			MenuItem menuItem = new MenuItem();
			menuItem.setText(token.name() + " (" + token.id().substring(0, 20) + "...)");
			ImageView icon = new ImageView(Utils.tokenIcon32x32(ErgoId.create(token.id())));
			icon.setFitWidth(32);
			icon.setFitHeight(32);
			menuItem.setGraphic(icon);
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
			HashMap<ErgoId, String> tokenNames = new HashMap<>(tokenList.getChildren().size());
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
				tokenNames.put(ErgoId.create(tokenLine.tokenInfo.id()), tokenLine.tokenInfo.name());
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
						return wallet.key().sign(ctx, unsignedTx);
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
					String explorerUrl = "https://" + (Main.programData().nodeNetworkType.get() == NetworkType.MAINNET ? "explorer" : "testnet") + ".ergoplatform.com/en/transactions";
					txLink.setOnAction(te -> Main.get().getHostServices().showDocument(explorerUrl + "/" + transactionId));
					copyTxId.setOnAction(ce -> Utils.copyStringToClipboard(transactionId));
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
				if (unsignedTxTask.getException() instanceof NotEnoughErgsException ex) {
					DecimalFormat exactNoLeading = new DecimalFormat("0");
					exactNoLeading.setMaximumFractionDigits(9);
					Utils.alert(Alert.AlertType.ERROR, Main.lang("youDoNotHaveEnoughErg_s_moreNeeded").formatted(exactNoLeading.format(ErgoInterface.toFullErg(amountNanoErg - ex.balanceFound))));
				} else if (unsignedTxTask.getException() instanceof NotEnoughTokensException ex) {
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
	public void showOptions(ActionEvent e) {
		Dialog<Pair<List<Integer>, Address>> dialog = new Dialog<>();
		dialog.initOwner(Main.get().stage());
		dialog.setTitle(Main.lang("settings")); // TODO use a specific string
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
						.map(ToggleButton::getUserData).map(ud -> (int) ud).toList(), ctrl.changeAddress.getValue().value());
			}
			return null;
		});
		dialog.showAndWait().ifPresent(v -> {
			candidates = v.getKey();
			change = v.getValue();
		});
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		candidates = new ArrayList<>(Main.get().getWallet().myAddresses.keySet());
		try {
			change = Main.get().getWallet().publicAddress(candidates.get(0));
		} catch (WalletKey.Failure e) {
			// won't happen because the master address is always either cached or available
		}
		txIdContainer.managedProperty().bind(txIdContainer.visibleProperty());
		paymentRequestIndicator.managedProperty().bind(paymentRequestIndicator.visibleProperty());
		address.textProperty().addListener((obs, o, n) -> {
			paymentRequestIndicator.setVisible(false);
			txIdContainer.setVisible(false);
		});
		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> {
			if (change.wasRemoved()) candidates.remove(change.getKey());
			else if (change.wasAdded()) candidates.add(change.getKey());
		});
		amount.textProperty().addListener((obs, o, n) -> txIdContainer.setVisible(false));
		fee.textProperty().addListener((obs, o, n) -> txIdContainer.setVisible(false));
		addToken.setDisable(Main.get().getWallet().lastKnownBalance.get() == null || Main.get().getWallet().lastKnownBalance.get().confirmedTokens().isEmpty());
		Main.get().getWallet().lastKnownBalance.addListener((obs, old, val) -> {
			addToken.setDisable(val == null || val.confirmedTokens().isEmpty());
		});
		if (Main.programData().blockchainNodeKind.get() == ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE) {
			Main.node.nodeBlocksLeft.addListener((observable, oldValue, newValue) -> {
				send.setDisable((int) newValue > 150);
				nodeSyncNotice.setVisible((int) newValue > 150);
			});
		}
	}
}
