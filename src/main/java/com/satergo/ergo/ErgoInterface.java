package com.satergo.ergo;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.SystemProperties;
import com.satergo.Utils;
import org.ergoplatform.appkit.*;
import org.ergoplatform.appkit.impl.OutBoxImpl;
import org.ergoplatform.sdk.ErgoId;
import org.ergoplatform.sdk.ErgoToken;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

import static com.satergo.Utils.HTTP;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class ErgoInterface {

	public static final String ERG_ID = "0".repeat(64);

	public static String getExplorerUrl(NetworkType networkType) {
		return switch (networkType) {
			case MAINNET -> SystemProperties.mainnetExplorerApi().orElse("https://api.ergoplatform.com");
			case TESTNET -> SystemProperties.testnetExplorerApi().orElse("https://api-testnet.ergoplatform.com");
		};
	}

	public static ErgoClient newNodeApiClient(NetworkType networkType, String nodeApiAddress) {
		return RestApiErgoClient.create(nodeApiAddress, networkType, "", getExplorerUrl(networkType));
	}

	public static ErgoProver newWithMnemonicProver(BlockchainContext ctx, boolean nonstandard, Mnemonic mnemonic, Iterable<Integer> derivedAddresses) {
		ErgoProverBuilder ergoProverBuilder = ctx.newProverBuilder().withMnemonic(mnemonic, nonstandard);
		derivedAddresses.forEach(ergoProverBuilder::withEip3Secret);
		return ergoProverBuilder.build();
	}

	public static Balance getBalance(NetworkType networkType, Address address) {
		// I don't want to use explorer here...
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(getExplorerUrl(networkType)).resolve("/api/v1/addresses/" + address + "/balance/total")).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, ofString()).body());
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

	public static int getNetworkBlockHeight(NetworkType networkType) {
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(getExplorerUrl(networkType) + "/blocks?limit=1&sortBy=height&sortDirection=desc")).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, ofString()).body());
			return body.getArray("items").getObject(0).getInt("height");
		} catch (JsonParserException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<InputBox> selectAllBoxes(List<Address> addresses, BlockchainContext ctx) {
		BoxOperations.ExplorerApiUnspentLoader loader = new ExplorerAndPoolUnspentBoxesLoader().withAllowChainedTx(true);
		ArrayList<InputBox> inputBoxes = new ArrayList<>();
		for (Address address : addresses) {
			int page = 0;
			while (true) {
				List<InputBox> result = loader.loadBoxesPage(ctx, address, page++);
				inputBoxes.addAll(result);
				if (result.size() < BlockchainContext.DEFAULT_LIMIT_FOR_API)
					break;
			}
		}
		return inputBoxes;
	}

	/**
	 * @param ergoClient ErgoClient, see for example {@link #newNodeApiClient}
	 * @param inputAddresses Input addresses
	 * @param recipient Address to send to
	 * @param amountToSend Amount to send (in nanoERGs)
	 * @param feeAmount Fee, minimum {@link Parameters#MinFee}
	 * @param changeAddress The address where leftover ERG or tokens from UTXOs will be sent
	 * @param tokensToSend Tokens to send
	 * @throws InputBoxesSelectionException If not enough ERG or not enough tokens were found
	 * @return The transaction ID with quotes around it
	 */
	public static UnsignedTransaction createUnsignedTransaction(ErgoClient ergoClient, List<Address> inputAddresses,
								  Address recipient, long amountToSend, long feeAmount, Address changeAddress, ErgoToken... tokensToSend) throws InputBoxesSelectionException {
		if (feeAmount < Parameters.MinFee) {
			throw new IllegalArgumentException("fee cannot be less than MinFee (" + Parameters.MinFee + " nanoERG)");
		}
		return ergoClient.execute(ctx -> {
			List<InputBox> boxesToSpend = BoxOperations.createForSenders(inputAddresses, ctx)
					.withAmountToSpend(amountToSend)
					.withFeeAmount(feeAmount)
					.withTokensToSpend(List.of(tokensToSend))
					.withInputBoxesLoader(new ExplorerAndPoolUnspentBoxesLoader().withAllowChainedTx(true))
					.loadTop();
			UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
			OutBoxBuilder newBoxBuilder = txBuilder.outBoxBuilder();
			newBoxBuilder.value(amountToSend);
			if (tokensToSend.length > 0) {
				newBoxBuilder.tokens(tokensToSend);
			}
			newBoxBuilder.contract(recipient.toErgoContract());
			OutBox newBox = newBoxBuilder.build();
			return txBuilder
					.addInputs(boxesToSpend.toArray(new InputBox[0])).addOutputs(newBox)
					.fee(feeAmount)
					.sendChangeTo(changeAddress)
					.build();
		});
	}

	/**
	 * @param inputAddresses Input addresses
	 * @param totalErg Amount to send (in nanoERGs)
	 * @param feeAmount Fee, minimum {@link Parameters#MinFee}
	 * @param changeAddress The address where leftover ERG or tokens from UTXOs will be sent
	 * @param totalTokens Tokens to send
	 * @throws InputBoxesSelectionException If not enough ERG or not enough tokens were found
	 * @return The transaction ID with quotes around it
	 */
	public static UnsignedTransaction createUnsignedTransaction(BlockchainContext ctx, List<Address> inputAddresses,
																List<OutBox> outBoxes, long totalErg, List<ErgoToken> totalTokens, long feeAmount, Address changeAddress) throws InputBoxesSelectionException {
		if (feeAmount < Parameters.MinFee) {
			throw new IllegalArgumentException("fee cannot be less than MinFee (" + Parameters.MinFee + " nanoERG)");
		}
		List<InputBox> boxesToSpend = BoxOperations.createForSenders(inputAddresses, ctx)
				.withAmountToSpend(totalErg)
				.withFeeAmount(feeAmount)
				.withTokensToSpend(totalTokens)
				.withInputBoxesLoader(new ExplorerAndPoolUnspentBoxesLoader().withAllowChainedTx(true))
				.loadTop();
		UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
		return txBuilder
				.addInputs(boxesToSpend.toArray(new InputBox[0]))
				.addOutputs(outBoxes.toArray(new OutBox[0]))
				.fee(feeAmount)
				.sendChangeTo(changeAddress)
				.build();
	}

	private static final long MIN_VALUE_PER_BYTE = 360;
	public static OutBox buildWithMinimumBoxValue(OutBoxBuilder outBoxBuilder, int boxIndex) {
		// Build a box with 0.001 to get the size that it would be
		int boxSize = ((OutBoxImpl) outBoxBuilder.value(toNanoErg(new BigDecimal("0.001"))).build())
				.getErgoBoxCandidate().toBox("0".repeat(64), (short) boxIndex).bytes().length;
		// Build a box with the calculated minimum ERG value
		outBoxBuilder.value(boxSize * MIN_VALUE_PER_BYTE);
		return outBoxBuilder.build();
	}

	public static JsonObject getTokenItem(NetworkType networkType, ErgoId tokenId) {
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(getExplorerUrl(networkType))
				.resolve("/api/v1/boxes/byTokenId/").resolve(tokenId.toString() + "/").resolve("?limit=1")).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, ofString()).body());
			return body.getArray("items").getObject(0);
		} catch (IOException | InterruptedException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static TokenInfo getTokenInfo(NetworkType networkType, ErgoId tokenId) {
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(getExplorerUrl(networkType))
				.resolve("/api/v1/tokens/").resolve(tokenId.toString())).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, ofString()).body());
			return new TokenInfo(body.getString("id"), body.getString("boxId"), body.getLong("emissionAmount"),
					body.getString("name"), body.getString("description"), body.getString("type"), body.getInt("decimals"));
		} catch (IOException | InterruptedException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static final int DEMURRAGE_EVERY = 1051200;
	public static OptionalInt getOldestUTXOCreationHeight(Address address) {
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create(getExplorerUrl(address.getNetworkType()))
				.resolve("/api/v1/boxes/unspent/byAddress/" + address + "?limit=1&sortDirection=desc")).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, ofString()).body());
			JsonArray items = body.getArray("items");
			if (items.isEmpty()) return OptionalInt.empty();
			return OptionalInt.of(items.getObject(0).getInt("creationHeight"));
		} catch (IOException | InterruptedException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static int getNetworkHeight() {
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.ergoplatform.com/api/v1/blocks?limit=1")).build();
		try {
			JsonObject body = JsonParser.object().from(HTTP.send(request, ofString()).body());
			return body.getArray("items").getObject(0).getInt("height");
		} catch (IOException | InterruptedException | JsonParserException e) {
			throw new RuntimeException(e);
		}
	}

	public static String generateMnemonicPhrase(String languageId) {
		return Mnemonic.generate(languageId, Mnemonic.DEFAULT_STRENGTH, Mnemonic.getEntropy(Mnemonic.DEFAULT_STRENGTH));
	}

	/**
	 * @param index 0 is the master address
	 */
	public static Address getPublicEip3Address(NetworkType networkType, boolean nonstandard, Mnemonic mnemonic, int index) {
		return Address.createEip3Address(index, networkType, mnemonic.getPhrase(), mnemonic.getPassword(), nonstandard);
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

	public static BigDecimal fullTokenAmount(long longTokenAmount, Integer decimals) {
		return BigDecimal.valueOf(longTokenAmount).movePointLeft(decimals == null ? 0 : decimals);
	}

	public static boolean hasValidNumberOfDecimals(BigDecimal fullErg) {
		return Utils.getNumberOfDecimalPlaces(fullErg) <= 9;
	}
}
