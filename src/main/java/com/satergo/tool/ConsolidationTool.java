package com.satergo.tool;

import com.satergo.FormatNumber;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.WalletKey;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoToken;

import java.util.List;

public class ConsolidationTool implements Tool {
	private final Tile tile;

	public ConsolidationTool() {
		tile = new Tile(1, 1, name());
		class EmptyWalletException extends RuntimeException {}
		class OnlyOneUTXOException extends RuntimeException {}
		tile.setOnAction(e -> {
			SatPromptDialog<Integer> dialog = new SatPromptDialog<>();
			dialog.initOwner(Main.get().stage());
			Main.get().applySameTheme(dialog.getScene());
			dialog.setHeaderText(Main.lang("consolidationInfo"));
			ComboBox<Integer> comboBox = new ComboBox<>();
			comboBox.getItems().addAll(Main.get().getWallet().myAddresses.keySet());
			comboBox.setConverter(new StringConverter<>() {
				@Override
				public String toString(Integer index) {
					String label = Main.get().getWallet().myAddresses.get(index);
					return label.isBlank() ? "#" + FormatNumber.integer(index) : label;
				}

				@Override
				public Integer fromString(String string) {
					throw new UnsupportedOperationException();
				}
			});
			comboBox.setValue(0);
			dialog.getDialogPane().setContent(comboBox);
			ButtonType ok = new ButtonType(Main.lang("consolidate"), ButtonBar.ButtonData.OK_DONE);
			dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
			dialog.setResultConverter(btn -> btn == ok ? comboBox.getValue() : null);
			dialog.showForResult().ifPresent(addressIndex -> {
				SatVoidDialog loading = new SatVoidDialog();
				loading.initOwner(Main.get().stage());
				Main.get().applySameTheme(loading.getScene());
				loading.setMoveStyle(MoveStyle.FOLLOW_OWNER);
				loading.setHeaderText(Main.lang("loadingUTXOs"));
				loading.show();
				SimpleBooleanProperty canBeClosed = new SimpleBooleanProperty(false);
				loading.setOnCloseRequest(event -> {
					if (!canBeClosed.get())
						event.consume();
				});
				Address address = Main.get().getWallet().publicAddress(addressIndex);
				List<Address> inputAddresses = Main.get().getWallet().addressStream().toList();
				Utils.createErgoClient().execute(ctx -> {
					new SimpleTask<>(() -> {
						UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
						List<InputBox> inputBoxes = ErgoInterface.selectAllBoxes(inputAddresses, ctx);
						if (inputBoxes.isEmpty())
							throw new EmptyWalletException();
						if (inputBoxes.size() == 1)
							throw new OnlyOneUTXOException();
						long totalErg = inputBoxes.stream().mapToLong(TransactionBox::getValue).sum();
						ErgoToken[] tokens = Utils.foldErgoTokens(inputBoxes.stream().flatMap(box -> box.getTokens().stream())).toArray(new ErgoToken[0]);
						long fee = Parameters.MinFee;
						OutBoxBuilder outBoxBuilder = txBuilder.outBoxBuilder()
								.contract(address.toErgoContract())
								.value(totalErg - fee);
						if (tokens.length > 0)
								outBoxBuilder.tokens(tokens);
						OutBox outBox = outBoxBuilder.build();
						return txBuilder
								.addInputs(inputBoxes.toArray(new InputBox[0]))
								.addOutputs(outBox)
								.fee(fee)
								// This will not be used but it is a requirement
								.sendChangeTo(address)
								.build();
					}).onSuccess(unsignedTx -> {
						canBeClosed.set(true);
						loading.close();
						try {
							SignedTransaction tx = Main.get().getWallet().key().sign(ctx, unsignedTx, Main.get().getWallet().myAddresses.keySet());
							new SimpleTask<>(() -> Main.get().getWallet().transact(tx))
									.onSuccess(txId -> {
										if (txId != null) Utils.textDialogWithCopy(Main.lang("transactionId"), txId);
									}).onFail(Utils::alertUnexpectedException)
									.newThread();
						} catch (WalletKey.Failure ex) {
							// user already informed
						}
					}).onFail(ex -> {
						canBeClosed.set(true);
						loading.close();
						if (ex instanceof EmptyWalletException) {
							Utils.alert(Alert.AlertType.INFORMATION, Main.lang("yourWalletIsEmpty"));
						} else if (ex instanceof OnlyOneUTXOException)
							Utils.alert(Alert.AlertType.INFORMATION, Main.lang("yourWalletOnlyOneUTXO"));
						else Utils.alertUnexpectedException(ex);
					})
							.newThread();
					return null;
				});
			});
		});

	}

	@Override
	public String name() {
		return Main.lang("tool.consolidateUTXOs");
	}

	@Override
	public Tile tile() {
		return tile;
	}
}
