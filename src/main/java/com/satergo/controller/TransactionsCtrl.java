package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Wallet;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.TransactionCell;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.explorer.client.DefaultApi;
import org.ergoplatform.explorer.client.model.TransactionInfo;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class TransactionsCtrl implements Initializable, WalletTab {

	private final Label loadingLabel = new Label(Main.lang("loading...")), emptyHistory = new Label(Main.lang("emptyTransactionHistory"));

	@FXML private Button refreshButton;
	@FXML private VBox finished;
	private DefaultApi api;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		finished.getChildren().setAll(loadingLabel);
		api = new Retrofit.Builder()
				.baseUrl(ErgoInterface.getExplorerUrl(Main.programData().nodeNetworkType.get()))
				.addConverterFactory(GsonConverterFactory.create())
				.build().create(DefaultApi.class);
		fetchHistory();
	}

	public SimpleTask<?> fetchHistory() {
		SimpleTask<List<TransactionInfo>> task = new SimpleTask<>(() -> {
			return Main.get().getWallet().addressStream().parallel()
					.map(address -> {
						try {
							return api.getApiV1AddressesP1Transactions(address.toString(), 0, 500, false)
									.execute()
									.body()
									.getItems();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}).flatMap(Collection::stream)
					// sort newest to oldest
					.sorted(Comparator.comparingLong(TransactionInfo::getTimestamp).reversed())
					// since all addresses are checked, there can be multiple entries of a transaction
					.distinct()
					.toList();
		}).onSuccess(transactions -> {
			finished.getChildren().clear();
			Wallet wallet = Main.get().getWallet();
			// the wallet could have been closed before the transactions loaded
			if (wallet == null) return;
			if (transactions.isEmpty()) {
				finished.getChildren().setAll(emptyHistory);
				return;
			}
			Set<Address> myAddresses = wallet.addressStream().collect(Collectors.toUnmodifiableSet());
			for (TransactionInfo tx : transactions) {
				finished.getChildren().add(new TransactionCell(tx, myAddresses));
			}
		});
		task.newThread();
		return task;
	}

	@FXML
	public void refresh() {
		finished.getChildren().setAll(loadingLabel);
		refreshButton.disableProperty().bind(fetchHistory().runningProperty());
	}
}
