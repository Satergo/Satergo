package com.satergo.tool;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.keystore.WalletKey;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatTextInputDialog;
import com.satergo.extra.dialog.SatVoidDialog;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoToken;

import java.util.List;

public class TransferEverythingTool implements Tool {
	private final Tile tile;

	public TransferEverythingTool() {
		tile = new Tile(1, 1, name());
		class EmptyWalletException extends RuntimeException {}
		class CancelledException extends RuntimeException {}
		tile.setOnAction(e -> {
			SatTextInputDialog dialog = new SatTextInputDialog();
			dialog.initOwner(Main.get().stage());
			Main.get().applySameTheme(dialog.getScene());
			dialog.setHeaderText(Main.lang("transferEverythingWarning"));
			ButtonType send = new ButtonType(Main.lang("send"), ButtonBar.ButtonData.OK_DONE);
			dialog.getDialogPane().getButtonTypes().setAll(send, ButtonType.CANCEL);
			dialog.showForResult().ifPresent(addr -> {
				SatVoidDialog loading = new SatVoidDialog();
				loading.initOwner(Main.get().stage());
				Main.get().applySameTheme(loading.getScene());
				loading.setMoveStyle(MoveStyle.FOLLOW_OWNER);
				loading.setHeaderText(Main.lang("loadingUTXOs"));
				loading.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
				loading.show();
				SimpleBooleanProperty cancelled = new SimpleBooleanProperty(false);
				loading.setOnHidden(event -> cancelled.set(true));

				Address recipient = Address.create(addr);
				List<Address> inputAddresses = Main.get().getWallet().addressStream().toList();
				Utils.createErgoClient().execute(ctx -> {
					new SimpleTask<>(() -> {
						UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
						List<InputBox> inputBoxes = ErgoInterface.selectAllBoxes(inputAddresses, ctx);
						if (cancelled.get())
							throw new CancelledException();
						if (inputBoxes.isEmpty())
							throw new EmptyWalletException();
						long totalErg = inputBoxes.stream().mapToLong(TransactionBox::getValue).sum();
						ErgoToken[] tokens = Utils.foldErgoTokens(inputBoxes.stream().flatMap(box -> box.getTokens().stream())).toArray(new ErgoToken[0]);
						long fee = Parameters.MinFee;
						OutBoxBuilder outBoxBuilder = txBuilder.outBoxBuilder()
								.contract(recipient.toErgoContract())
								.value(totalErg - fee);
						if (tokens.length > 0)
							outBoxBuilder.tokens(tokens);
						OutBox outBox = outBoxBuilder.build();
						return txBuilder
								.addInputs(inputBoxes.toArray(new InputBox[0]))
								.addOutputs(outBox)
								.fee(fee)
								// This will not be used, but it is required to call it
								.sendChangeTo(recipient)
								.build();
					}).onSuccess(unsignedTx -> {
						try {
							if (cancelled.get()) return;
							loading.close();
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
						if (ex instanceof EmptyWalletException) {
							loading.close();
							Utils.alert(Alert.AlertType.INFORMATION, Main.lang("yourWalletIsEmpty"));
						} else if (!(ex instanceof CancelledException)) {
							loading.close();
							Utils.alertUnexpectedException(ex);
						}
					}).newThread();
					return null;
				});
			});
		});
	}

	@Override
	public String name() {
		return Main.lang("tool.transferEverything");
	}

	@Override
	public Tile tile() {
		return tile;
	}
}
