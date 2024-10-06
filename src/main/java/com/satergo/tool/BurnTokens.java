package com.satergo.tool;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.WalletKey;
import com.satergo.ergo.TokenBalance;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.geometry.HPos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoToken;

import java.util.List;
import java.util.stream.Collectors;

public class BurnTokens implements Tool {
	private final Tile tile;

	public BurnTokens() {
		tile = new Tile(1, 1, name());
		tile.setOnAction(event -> {
			SatPromptDialog<List<TokenBalance>> dialog = new SatPromptDialog<>();
			dialog.initOwner(Main.get().stage());
			Main.get().applySameTheme(dialog.getDialogPane().getScene());
			dialog.setMaxHeight(tile.getScene().getWindow().getHeight());
			dialog.setTitle(Main.lang("selectTokensToBurn"));
			GridPane grid = new GridPane();
			grid.setHgap(4);
			ColumnConstraints col0 = new ColumnConstraints();
			col0.setHalignment(HPos.CENTER);
			grid.getColumnConstraints().addAll(col0);
			grid.add(new Label(Main.lang("burn")), 0, 0);
			grid.add(new Label(Main.lang("tokenName")), 1, 0);
			grid.add(new Label(Main.lang("tokenId")), 2, 0);
			grid.add(new Label(Main.lang("amount")), 3, 0);
			List<TokenBalance> tokenBalances = Main.get().getWallet().lastKnownBalance.get().confirmedTokens();
			if (tokenBalances.isEmpty()) {
				Utils.alert(Alert.AlertType.INFORMATION, Main.lang("yourWalletHasNoTokens"));
				return;
			}
			for (int i = 0; i < tokenBalances.size(); i++) {
				TokenBalance tb = tokenBalances.get(i);
				CheckBox checkBox = new CheckBox();
				checkBox.setUserData(tb);
				grid.add(checkBox, 0, i + 1);
				Label name = new Label(tb.name());
				name.setLabelFor(checkBox);
				grid.add(name, 1, i + 1);
				grid.add(new Label(tb.id()), 2, i + 1);
				grid.add(new Label(tb.fullAmount().toPlainString()), 3, i + 1);
			}
			dialog.getDialogPane().setContent(new ScrollPane(grid));
			ButtonType burn = new ButtonType(Main.lang("burn"), ButtonBar.ButtonData.OK_DONE);
			dialog.getDialogPane().getButtonTypes().addAll(burn, ButtonType.CANCEL);
			dialog.setResultConverter(param -> {
				if (param == burn) return grid.getChildren().stream()
						.filter(c -> c instanceof CheckBox)
						.filter(c -> ((CheckBox) c).isSelected())
						.map(c -> ((TokenBalance) c.getUserData()))
						.toList();
				return null;
			});
			dialog.showForResult().ifPresent(tokensToBurn -> {
				if (tokensToBurn.isEmpty()) {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("noTokensSelectedBurn"));
					return;
				}
				SatPromptDialog<Boolean> confirmation = new SatPromptDialog<>();
				confirmation.initOwner(Main.get().stage());
				Main.get().applySameTheme(confirmation.getDialogPane().getScene());
				confirmation.setHeaderText(Main.lang("tokenBurnConfirmation"));
				confirmation.getDialogPane().setContent(new Label(tokensToBurn.stream().map(t -> t.name() + " (" + t.id() + "): " + t.fullAmount().toPlainString()).collect(Collectors.joining("\n"))));
				confirmation.getDialogPane().getButtonTypes().addAll(ButtonType.YES, ButtonType.CANCEL);
				confirmation.setResultConverter(t -> t == ButtonType.YES);
				confirmation.showForResult().ifPresent(confirm -> {
					if (!confirm) return;
					Wallet wallet = Main.get().getWallet();
					new SimpleTask<>(() -> Utils.createErgoClient().execute(ctx -> {
						long fee = Parameters.MinFee;
						List<ErgoToken> ergoTokensToBurn = tokensToBurn.stream().map(t -> new ErgoToken(t.id(), t.amount())).toList();
						List<InputBox> boxesToSpend = BoxOperations.createForSenders(wallet.addressStream().toList(), ctx)
								.withAmountToSpend(0)
								.withFeeAmount(fee)
								.withTokensToSpend(ergoTokensToBurn)
								.withInputBoxesLoader(new ExplorerAndPoolUnspentBoxesLoader().withAllowChainedTx(true))
								.loadTop();
						UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();

						// Manually build a change box that does not contain the tokens to be burnt
						long totalErg = boxesToSpend.stream().mapToLong(TransactionBox::getValue).sum();
						ErgoToken[] totalTokens = Utils.foldErgoTokens(boxesToSpend.stream()
								.flatMap(box -> box.getTokens().stream())
								.filter(boxToken -> ergoTokensToBurn.stream().noneMatch(toBurn -> boxToken.getId().equals(toBurn.id())))
						).toArray(new ErgoToken[0]);
						Address master = wallet.publicAddress(0);
						OutBoxBuilder newBoxBuilder = txBuilder.outBoxBuilder()
								.contract(master.toErgoContract())
								.value(totalErg - fee);
						if (totalTokens.length > 0)
							newBoxBuilder.tokens(totalTokens);
						OutBox newBox = newBoxBuilder.build();
						return txBuilder
								.addInputs(boxesToSpend.toArray(new InputBox[0]))
								.addOutputs(newBox)
								.fee(fee)
								// This will not be used, but it is required to call it
								// It would be nice to add no boxes and only have the change but Appkit does not allow that
								// so we build the change box manually and call this just to fulfill the requirement
								.sendChangeTo(master)
								.tokensToBurn(ergoTokensToBurn.toArray(new ErgoToken[0]))
								.build();
					})).onSuccess(unsignedTx -> {
						SignedTransaction signedTx = Utils.createErgoClient().execute(ctx -> {
							try {
								return wallet.key().sign(ctx, unsignedTx, wallet.myAddresses.keySet());
							} catch (WalletKey.Failure e) {
								// user already informed
								return null;
							}
						});
						if (signedTx == null) return;
						new SimpleTask<>(() -> wallet.transact(signedTx))
								.onSuccess(txId -> {
									Utils.textDialogWithCopy(Main.lang("transactionId"), txId);
								})
								.onFail(Utils::alertUnexpectedException)
								.newThread();
					}).onFail(Utils::alertUnexpectedException).newThread();
				});
			});
		});
	}

	@Override
	public String name() {
		return Main.lang("tool.burnTokens");
	}

	@Override
	public Tile tile() {
		return tile;
	}
}
