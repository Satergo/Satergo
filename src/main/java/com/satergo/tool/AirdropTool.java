package com.satergo.tool;

import com.satergo.*;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenBalance;
import com.satergo.ergo.TokenSummary;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.keystore.WalletKey;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoId;
import org.ergoplatform.sdk.ErgoToken;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AirdropTool implements Tool {
	private final Tile tile;

	public AirdropTool() {
		tile = new Tile(1, 1, name());
		tile.clickable.set(true);
		tile.setOnAction(event -> {
			SatPromptDialog<ButtonType> dialog = new SatPromptDialog<>();
			dialog.initOwner(Main.get().stage());
			Main.get().applySameTheme(dialog.getDialogPane().getScene());
			dialog.setTitle(Main.lang("tool.airdrop"));

			// Instructions
			String instr = Main.lang("airdropFileInstructions");
			int idPos = instr.indexOf("%s");
			String instrStart = instr.substring(0, idPos);
			String instrEnd = instr.substring(idPos + 2);
			TextFlow textFlow = new TextFlow();
			textFlow.setMaxWidth(Main.get().stage().getWidth() * 0.7);
			textFlow.setTextAlignment(TextAlignment.CENTER);
			textFlow.getChildren().add(new Text(instrStart));
			Label ergId = new Label(ErgoInterface.ERG_ID);
			Utils.addCopyContextMenu(ergId);
			textFlow.getChildren().add(ergId);
			textFlow.getChildren().add(new Text(instrEnd));
			dialog.getDialogPane().setContent(textFlow);

			dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			ButtonType buttonType = dialog.showForResult().orElse(null);
			if (buttonType != ButtonType.OK) return;
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle(Main.lang("selectAirdropFile"));
			fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(Main.lang("csvOrTsvFile"), "*.csv", "*.tsv"));
			File file = fileChooser.showOpenDialog(tile.getScene().getWindow());
			if (file == null) return;
			try {
				String delimiter;
				if (Pattern.compile("(?i)\\.csv$").matcher(file.toString()).find())
					delimiter = ",";
				else if (Pattern.compile("(?i)\\.tsv$").matcher(file.toString()).find())
					delimiter = "\t";
				else {
					Utils.alert(Alert.AlertType.ERROR, Main.lang("unknownFileType"));
					return;
				}
				List<String> lines = Files.readAllLines(file.toPath());
				createAirdrop(lines, delimiter).handle((airdrop, ex) -> {
					if (ex != null) {
						if (ex instanceof AirdropException e) {
							Utils.alert(Alert.AlertType.ERROR, e.getMessage());
						} else {
							Utils.alertUnexpectedException(ex);
						}
						return null;
					}
					if (airdrop == null) return null;
					SatPromptDialog<ButtonType> summaryDialog = new SatPromptDialog<>();
					summaryDialog.initOwner(Main.get().stage());
					Main.get().applySameTheme(summaryDialog.getDialogPane().getScene());
					summaryDialog.setTitle(Main.lang("tool.airdrop"));
					summaryDialog.getDialogPane().setContent(new Label(Main.lang("summaryOfOutgoing") + "\n\n"
							+ FormatNumber.ergExact(ErgoInterface.toFullErg(airdrop.ergToSpend)) + " ERG\n"
							+ airdrop.tokensToSpend.stream().map(t -> t.name() + " (" + t.id() + "): " + t.fullAmount().toPlainString()).collect(Collectors.joining("\n"))));
					ButtonType send = new ButtonType(Main.lang("send"), ButtonBar.ButtonData.OK_DONE);
					summaryDialog.getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);
					ButtonType t = summaryDialog.showForResult().orElse(null);
					if (t != send) return null;
					SignedTransaction tx = Utils.createErgoClient().execute(ctx -> {
						try {
							return Main.get().getWallet().key().sign(ctx, airdrop.unsignedTx, Main.get().getWallet().myAddresses.keySet());
						} catch (WalletKey.Failure e) {
							return null;
						}
					});
					if (tx == null) return null;
					new SimpleTask<>(() -> Main.get().getWallet().transact(tx))
							.onSuccess(txId -> {
								if (txId != null) Utils.textDialogWithCopy(Main.lang("transactionId"), txId);
							}).onFail(Utils::alertUnexpectedException)
							.newThread();
					return null;
				});
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static class AirdropException extends RuntimeException {
		public AirdropException(String message) {
			super(message);
		}
	}
	private record Airdrop(UnsignedTransaction unsignedTx, long ergToSpend, List<TokenBalance> tokensToSpend) {}
	private static CompletableFuture<Airdrop> createAirdrop(List<String> lines, String delimiter) {
		class Output {
			Long value = null;
			final HashMap<String, Long> tokens = new HashMap<>();
		}
		CompletableFuture<Airdrop> cf = new CompletableFuture<>();
		Utils.createErgoClient().execute(ctx -> {
			Wallet wallet = Main.get().getWallet();
			UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
			HashMap<Address, Output> outputMap = new LinkedHashMap<>();
			HashMap<String, TokenSummary> tokenInfo = new HashMap<>();
			HashMap<String, Long> tokensSpent = new HashMap<>();
			ArrayList<OutBox> outBoxes = new ArrayList<>();
			for (String line : lines) {
				String[] parts = line.split(delimiter);
				if (parts.length != 3) {
					cf.completeExceptionally(new AirdropException(Main.lang("invalidAirdropFile")));
					return null;
				}
				Address address = Address.create(parts[0]);
				String token = parts[1];
				// Only to check validity
				ErgoId.create(token);
				boolean isErg = token.equals(ErgoInterface.ERG_ID);
				BigDecimal amount = new BigDecimal(parts[2]);
				Output output = outputMap.computeIfAbsent(address, key -> new Output());
				if (isErg) {
					if (!ErgoInterface.hasValidNumberOfDecimals(amount)) {
						cf.completeExceptionally(new AirdropException(Main.lang("amountHasTooManyDecimals")));
						return null;
					}
					output.value = ErgoInterface.toNanoErg(amount);
				} else {
					TokenBalance tokenBalance = wallet.lastKnownBalance.get().confirmedTokens().stream().filter(tb -> tb.id().equals(token)).findAny().orElseThrow();
					int tokenDecimals = tokenBalance.decimals();
					if (Utils.getNumberOfDecimalPlaces(amount) > tokenDecimals) {
						cf.completeExceptionally(new AirdropException(Main.lang("token_s_hasInvalidAmount").formatted(tokenBalance.name())));
						return null;
					}
					long longAmount = ErgoInterface.longTokenAmount(amount, tokenDecimals);
					tokenInfo.put(token, tokenBalance);
					tokensSpent.put(token, tokensSpent.getOrDefault(token, 0L) + longAmount);
					output.tokens.put(token, output.tokens.getOrDefault(token, 0L) + longAmount);
				}
			}
			int index = 0;
			for (Map.Entry<Address, Output> entry : outputMap.entrySet()) {
				Address address = entry.getKey();
				Output output = entry.getValue();
				OutBoxBuilder builder = txBuilder.outBoxBuilder();
				builder.contract(address.toErgoContract());
				if (output.value == null && output.tokens.isEmpty()) {
					cf.completeExceptionally(new IllegalStateException());
					return null;
				}
				if (!output.tokens.isEmpty())
					builder.tokens(Utils.foldErgoTokens(output.tokens.entrySet().stream().map(e -> new ErgoToken(e.getKey(), e.getValue()))).toArray(new ErgoToken[0]));
				if (output.value == null)
					outBoxes.add(ErgoInterface.buildWithMinimumBoxValue(builder, index));
				else outBoxes.add(builder.value(output.value).build());
				index++;
			}
			long ergSpent = outBoxes.stream().mapToLong(OutBox::getValue).sum();
			long fee = Parameters.MinFee;
			List<ErgoToken> tokenList = tokensSpent.entrySet().stream().map(e -> new ErgoToken(e.getKey(), e.getValue())).toList();
			new SimpleTask<>(() -> {
				List<InputBox> inputBoxes = ErgoInterface.boxSelector(ctx, wallet.addressStream().toList(), ergSpent, tokenList, fee).loadTop();
				return txBuilder
						.addInputs(inputBoxes.toArray(new InputBox[0]))
						.addOutputs(outBoxes.toArray(new OutBox[0]))
						.fee(fee)
						.sendChangeTo(wallet.publicAddress(0))
						.build();
			}).onSuccess(unsignedTx -> {
				cf.complete(new Airdrop(
						unsignedTx,
						ergSpent + fee,
						tokensSpent.entrySet().stream().map(e -> new TokenBalance(e.getKey(), e.getValue(), tokenInfo.get(e.getKey()).decimals(), tokenInfo.get(e.getKey()).name())).toList()));
			}).onFail(t -> {
				Utils.alertTxBuildException(t, ergSpent, tokenList, id -> tokenInfo.get(id.toString()).name());
				cf.completeExceptionally(t);
			}).newThread();
			return null;
		});
		return cf;
	}

	@Override
	public String name() {
		return Main.lang("tool.airdrop");
	}

	@Override
	public Tile tile() {
		return tile;
	}
}
