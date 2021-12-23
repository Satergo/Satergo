package com.satergo.ergo;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.Utils;
import org.ergoplatform.appkit.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Function;

public class ErgoInterface {

	public static ErgoClient newNodeApiClient(NetworkType networkType, String nodeApiAddress) {
		return RestApiErgoClient.create(nodeApiAddress, networkType, "", RestApiErgoClient.getDefaultExplorerUrl(networkType));
	}

	public static ErgoProver newWithMnemonicProver(BlockchainContext ctx, Mnemonic mnemonic) {
		return ctx.newProverBuilder().withMnemonic(mnemonic).withEip3Secret(0).build();
	}

	public static Balance getBalance(NetworkType networkType, Address address) {
		// I don't want to use explorer here...
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(RestApiErgoClient.getDefaultExplorerUrl(networkType) + "/api/v1/addresses/" + address + "/balance/total")).build();
		try {
			JsonObject body = JsonParser.object().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
			JsonObject confirmed = body.getObject("confirmed");
			JsonObject unconfirmed = body.getObject("unconfirmed");
			Function<JsonObject, TokenBalance> tokenDeserialize = obj -> new TokenBalance(obj.getString("tokenId"), obj.getLong("amount"), obj.getInt("decimals"), obj.getString("name"));
			return new Balance(
					confirmed.getLong("nanoErgs"),
					unconfirmed.getLong("nanoErgs"),
					confirmed.getArray("tokens").stream().map(raw -> (JsonObject) raw).map(tokenDeserialize).toList(),
					unconfirmed.getArray("tokens").stream().map(raw -> (JsonObject) raw).map(tokenDeserialize).toList());
		} catch (JsonParserException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static int getNodeBlockHeight(String nodeApiAddress) {
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(nodeApiAddress).resolve("/blocks/lastHeaders/1")).build();
		try {
			JsonArray body = JsonParser.array().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
			if (body.isEmpty()) return 0;
			return body.getObject(0).getInt("height");
		} catch (JsonParserException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static int getNetworkBlockHeight(NetworkType networkType) {
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(RestApiErgoClient.getDefaultExplorerUrl(networkType) + "/blocks?limit=1&sortBy=height&sortDirection=desc")).build();
		try {
			JsonObject body = JsonParser.object().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
			return body.getArray("items").getObject(0).getInt("height");
		} catch (JsonParserException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param ergoClient ErgoClient, see for example {@link #newNodeApiClient}
	 * @param ergoProverFunction function that creates the ErgoProver using a BlockchainContext, see for example {@link #newWithMnemonicProver}
	 * @param recipient address to send to
	 * @param amountToSend amount to send in nanoERGs
	 * @param feeAmount Fee, minimum {@link Parameters#MinFee}
	 * @param tokensToSend tokens to send
	 * @return the transaction ID
	 */
	public static String transact(ErgoClient ergoClient, Function<BlockchainContext, ErgoProver> ergoProverFunction, Address recipient, long amountToSend, long feeAmount, ErgoToken... tokensToSend) {
		if (feeAmount < Parameters.MinFee) {
			throw new IllegalArgumentException("fee cannot be less than MinFee (" + Parameters.MinFee + " nanoERG)");
		}

		return ergoClient.execute(ctx -> {
			ErgoProver senderProver = ergoProverFunction.apply(ctx);
			Address sender = senderProver.getEip3Addresses().get(0);
			List<InputBox> unspent = ctx.getUnspentBoxesFor(sender, 0, 20);
			System.out.println("unspent = " + unspent);
			List<InputBox> boxesToSpend = BoxOperations.selectTop(unspent, amountToSend + feeAmount, List.of(tokensToSend));
			UnsignedTransactionBuilder txB = ctx.newTxBuilder();
			OutBoxBuilder newBoxBuilder = txB.outBoxBuilder();
			newBoxBuilder.value(amountToSend);
			if (tokensToSend.length > 0) {
				newBoxBuilder.tokens(tokensToSend);
			}
			newBoxBuilder.contract(ctx.compileContract(ConstantsBuilder.create()
					.item("recipientPk", recipient.getPublicKey())
					.build(), "{ recipientPk }")).build();
			OutBox newBox = newBoxBuilder.build();
			UnsignedTransaction unsignedTx = txB
					.boxesToSpend(boxesToSpend).outputs(newBox)
					.fee(feeAmount)
					.sendChangeTo(senderProver.getEip3Addresses().get(0).asP2PK())
					.build();
			SignedTransaction signedTx = senderProver.sign(unsignedTx);
			return ctx.sendTransaction(signedTx);
		});
	}


	public static String generateMnemonicPhrase(String languageId) {
		return Mnemonic.generate(languageId, Mnemonic.DEFAULT_STRENGTH, Mnemonic.getEntropy(Mnemonic.DEFAULT_STRENGTH));
	}


	/**
	 * @param index 0 is the master address
	 */
	public static Address getPublicEip3Address(NetworkType networkType, Mnemonic mnemonic, int index) {
		return Address.createEip3Address(index, networkType, mnemonic.getPhrase(), mnemonic.getPassword());
	}


	public static long toNanoErg(BigDecimal fullErg) {
		return fullErg.movePointRight(9).longValueExact();
	}

	public static BigDecimal toFullErg(long nanoErg) {
		return BigDecimal.valueOf(nanoErg).movePointLeft(9);
	}

	public static long longTokenAmount(BigDecimal fullTokenAmount, int decimals) {
		return fullTokenAmount.movePointRight(decimals).longValue();
	}

	public static BigDecimal fullTokenAmount(long longTokenAmount, int decimals) {
		return BigDecimal.valueOf(longTokenAmount).movePointLeft(decimals);
	}

	public static boolean hasValidNumberOfDecimals(BigDecimal fullErg) {
		return Utils.getNumberOfDecimalPlaces(fullErg) <= 9;
	}
}
