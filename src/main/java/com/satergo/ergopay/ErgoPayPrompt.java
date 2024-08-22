package com.satergo.ergopay;

import com.satergo.FormatNumber;
import com.satergo.Load;
import com.satergo.Main;
import com.satergo.WalletKey;
import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenBalance;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.OutBox;
import org.ergoplatform.appkit.ReducedTransaction;
import org.ergoplatform.explorer.client.DefaultApi;
import org.ergoplatform.explorer.client.model.AssetInstanceInfo;
import org.ergoplatform.explorer.client.model.OutputInfo;
import org.ergoplatform.sdk.ErgoToken;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ErgoPayPrompt extends SatPromptDialog<Boolean> {

	public ErgoPayPrompt(ErgoPay.Request request) {
		getDialogPane().getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);

		ReducedTransaction reducedTx = request.reducedTx();
		DefaultApi api = new Retrofit.Builder()
				.baseUrl(ErgoInterface.getExplorerUrl(Main.programData().nodeNetworkType.get()))
				.addConverterFactory(GsonConverterFactory.create())
				.build().create(DefaultApi.class);

		// Ignore the class name...
		List<OutputInfo> inputBoxes = reducedTx.getInputBoxesIds()
				.parallelStream()
				.map(api::getApiV1BoxesP1)
				.map(x -> {
					try {
						return x.execute().body();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				})
				.toList();

		if (inputBoxes.contains(null))
			throw new IllegalStateException();

		long ergSpent = 0;
		ArrayList<TokenBalance> totalTokensSpent = new ArrayList<>();
		HashSet<Address> affectedAddresses = new HashSet<>();

		// Calculate the total amount of ERG and tokens in the input boxes
		for (OutputInfo inputBox : inputBoxes) {
			ergSpent += inputBox.getValue();
			affectedAddresses.add(Address.create(inputBox.getAddress()));
			tokenLoop:
			for (AssetInstanceInfo token : inputBox.getAssets()) {
				ListIterator<TokenBalance> iter = totalTokensSpent.listIterator();
				while (iter.hasNext()) {
					TokenBalance tokenBalance = iter.next();
					if (tokenBalance.id().equals(token.getTokenId())) {
						iter.set(tokenBalance.withAmount(tokenBalance.amount() + token.getAmount()));
						continue tokenLoop;
					}
				}
				totalTokensSpent.add(new TokenBalance(token.getTokenId(), token.getAmount(), token.getDecimals(), token.getName()));
			}
		}
		// Subtract the amount of ERG and tokens that are sent to own addresses
		for (OutBox output : reducedTx.getOutputs()) {
			Address to = Address.fromErgoTree(output.getErgoTree(), Main.programData().nodeNetworkType.get());
			if (Main.get().getWallet().addressStream().anyMatch(a -> a.equals(to))) {
				ergSpent -= output.getValue();
				tokenLoop:
				for (ErgoToken token : output.getTokens()) {
					ListIterator<TokenBalance> iter = totalTokensSpent.listIterator();
					while (iter.hasNext()) {
						TokenBalance tokenBalance = iter.next();
						if (tokenBalance.id().equals(token.getId().toString())) {
							iter.set(tokenBalance.withAmount(tokenBalance.amount() - token.getValue()));
							continue tokenLoop;
						}
					}
				}
			}
		}
		totalTokensSpent.removeIf(tokenBalance -> tokenBalance.amount() == 0L);

		// todo SignRequest message

		GridPane root = new GridPane() {
			{ Load.thisFxml(this, "/dialog/ergopay.fxml"); }
		};
		int row = 3;
		root.add(new Label("ERG:"), 0, row);
		root.add(new Label(FormatNumber.ergExact(ErgoInterface.toFullErg(ergSpent))), 1, row);
		row++;
		for (TokenBalance tokenBalance : totalTokensSpent) {
			root.add(new Label(tokenBalance.name() + ":"), 0, row);
			root.add(new Label("-" + FormatNumber.tokenExact(tokenBalance)), 1, row);
			row++;
		}
		HashMap<Address, Integer> walletAddresses = new HashMap<>();
		Main.get().getWallet().myAddresses.forEach((index, name) -> {
			walletAddresses.put(Main.get().getWallet().publicAddress(index), index);
		});
		root.add(new Label(Main.lang("ergoPay.affectedAddressesC") + " " + affectedAddresses.stream()
				.filter(walletAddresses::containsKey)
				.map(walletAddresses::get)
				.map(Main.get().getWallet().myAddresses::get)
				.collect(Collectors.joining(", "))), 0, row, 2, 1);
		getDialogPane().setContent(root);

		setResultConverter(param -> param == ButtonType.YES);
	}
}
