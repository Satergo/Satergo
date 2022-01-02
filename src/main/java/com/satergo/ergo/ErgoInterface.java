package com.satergo.ergo;

import com.grack.nanojson.*;
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

	public static ErgoProver newWithMnemonicProver(BlockchainContext ctx, Mnemonic mnemonic, Iterable<Integer> derivedAddresses) {
		ErgoProverBuilder ergoProverBuilder = ctx.newProverBuilder().withMnemonic(mnemonic);
		derivedAddresses.forEach(ergoProverBuilder::withEip3Secret);
		return ergoProverBuilder.build();
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
	 * @param ergoProverFunction Function that creates the ErgoProver using a BlockchainContext, see for example {@link #newWithMnemonicProver}
	 * @param recipient Address to send to
	 * @param amountToSend Amount to send (in nanoERGs)
	 * @param feeAmount Fee, minimum {@link Parameters#MinFee}
	 * @param changeAddress The address where leftover ERGs or tokens from UTXOs will be sent
	 * @param tokensToSend Tokens to send
	 * @throws InputBoxesSelectionException If not enough ERG or not enough tokens were found
	 * @return The transaction ID with quotes around it
	 */
	public static String transact(ErgoClient ergoClient, Function<BlockchainContext, ErgoProver> ergoProverFunction,
								  Address recipient, long amountToSend, long feeAmount, Address changeAddress, ErgoToken... tokensToSend) throws InputBoxesSelectionException {
		if (feeAmount < Parameters.MinFee) {
			throw new IllegalArgumentException("fee cannot be less than MinFee (" + Parameters.MinFee + " nanoERG)");
		}
		return ergoClient.execute(ctx -> {
			ErgoProver senderProver = ergoProverFunction.apply(ctx);
			Address sender = senderProver.getEip3Addresses().get(0);
			List<InputBox> unspent = senderProver.getEip3Addresses().stream().flatMap(address -> ctx.getUnspentBoxesFor(address, 0, 20).stream()).toList();
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

	public enum UnlockingResult { INCORRECT_API_KEY, INCORRECT_PASSWORD, SUCCESS }
	public static UnlockingResult unlockServerNodeWallet(String nodeApiAddress, String apiKey, String password) {
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(nodeApiAddress).resolve("/wallet/unlock"))
				.header("Content-Type", "application/json")
				.header("api_key", apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(JsonWriter.string().object().value("pass", password).end().done())).build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 403) {
				return UnlockingResult.INCORRECT_API_KEY;
			} else if (response.statusCode() == 400) {
				return UnlockingResult.INCORRECT_PASSWORD;
			} else if (response.statusCode() == 200) {
				return UnlockingResult.SUCCESS;
			}
			System.out.println("response.statusCode() = " + response.statusCode() + " ; " + response.body());
			return null;
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
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
