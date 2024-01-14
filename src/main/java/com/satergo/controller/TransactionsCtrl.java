package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Wallet;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.TransactionCell;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.explorer.client.DefaultApi;
import org.ergoplatform.explorer.client.model.TransactionInfo;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

public class TransactionsCtrl implements Initializable, WalletTab {

	@FXML private Button refreshButton;
	@FXML private VBox pending, finished;
	private DefaultApi api;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		api = new Retrofit.Builder()
				.baseUrl(ErgoInterface.getExplorerUrl(Main.programData().nodeNetworkType.get()))
				.addConverterFactory(GsonConverterFactory.create())
				.build().create(DefaultApi.class);
		refresh();
	}

	public SimpleTask<?> refresh() {
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
		}).onSuccess(list -> {
			finished.getChildren().clear();
			Wallet wallet = Main.get().getWallet();
			// the wallet could have been closed before the transactions loaded
			if (wallet == null) return;
			List<Address> myAddresses = wallet.addressStream().toList();
			for (TransactionInfo t : list) {
				finished.getChildren().add(new TransactionCell(t, myAddresses));
			}
		});
		task.newThread();
		return task;
	}

	@FXML
	public void refreshAction() {
		refreshButton.disableProperty().bind(refresh().runningProperty());
	}
}
