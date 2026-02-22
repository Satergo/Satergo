package com.satergo.controller;

import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.Wallet;
import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.CellForSpacing;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.TransactionCell;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.explorer.client.DefaultApi;
import org.ergoplatform.explorer.client.model.TransactionInfo;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.satergo.extra.TransactionCell.totalReceived;
import static com.satergo.extra.TransactionCell.totalSent;

public class TransactionsCtrl implements Initializable, WalletTab {

	private final Label loadingLabel = Utils.accessibleLabel(new Label(Main.lang("loading..."))),
			emptyHistory = Utils.accessibleLabel(new Label(Main.lang("emptyTransactionHistory")));
	public VBox root;
	public TitledPane filterPane;

	@FXML private Button refreshButton;
	@FXML private Pane container;

	@FXML private TextField search;
	@FXML private CheckBox filterMinErg, filterMaxErg;
	@FXML private TextField valueMinErg, valueMaxErg;
	@FXML private CheckBox filterMinDate, filterMaxDate;
	@FXML private TextField valueMinDate, valueMaxDate;

	private DefaultApi api;

	private final ObservableList<TransactionInfo> allTransactions = FXCollections.observableArrayList();
	private final FilteredList<TransactionInfo> shownTranslations = new FilteredList<>(allTransactions);
	private VirtualizedScrollPane<?> scrollPane;
	private Set<String> myAddresses;

	private static final Predicate<TransactionInfo> FILTER_OFF = _ -> true;
	private static final Predicate<TransactionInfo> FILTER_INVALID = null;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		container.getChildren().setAll(loadingLabel);
		api = new Retrofit.Builder()
				.baseUrl(ErgoInterface.getExplorerUrl(Main.programData().nodeNetworkType.get()))
				.addConverterFactory(GsonConverterFactory.create())
				.build().create(DefaultApi.class);

		filterMinDate.setText(Main.lang("date") + " " + filterMinDate.getText());
		filterMaxDate.setText(Main.lang("date") + " " + filterMaxDate.getText());

		VirtualFlow<TransactionInfo, CellForSpacing<TransactionInfo, TransactionCell>> flow = VirtualFlow.createVertical(shownTranslations, info -> {
			return new CellForSpacing<>(new TransactionCell(info, myAddresses), 10, Orientation.VERTICAL);
		});
		VirtualizedScrollPane<VirtualFlow<TransactionInfo, CellForSpacing<TransactionInfo, TransactionCell>>> scrollPane = new VirtualizedScrollPane<>(flow);
		this.scrollPane = scrollPane;
		VBox.setVgrow(scrollPane, Priority.ALWAYS);

		shownTranslations.predicateProperty().bind(Bindings.createObjectBinding(() -> {
			BigDecimal min, max;
			if (filterMinErg.isSelected() && !valueMinErg.getText().isBlank()) {
				try {
					min = new BigDecimal(valueMinErg.getText());
				} catch (Exception e) {
					return FILTER_INVALID;
				}
			} else min = null;
			if (filterMaxErg.isSelected() && !valueMaxErg.getText().isBlank()) {
				try {
					max = new BigDecimal(valueMaxErg.getText());
				} catch (Exception e) {
					return FILTER_INVALID;
				}
			} else max = null;
			// Invalid state, min > max.
			if (min != null && max != null && min.compareTo(max) > 0)
				return FILTER_INVALID;
			String searchQuery;
			if (!search.getText().isBlank()) {
				searchQuery = search.getText().strip();
			} else searchQuery = null;
			Long minDate, maxDate;
			if (filterMinDate.isSelected() && !valueMinDate.getText().isBlank()) {
				try {
					minDate = LocalDate.parse(valueMinDate.getText()).atStartOfDay(ZoneOffset.systemDefault()).toInstant().toEpochMilli();
				} catch (Exception e) {
					return FILTER_INVALID;
				}
			} else minDate = null;
			if (filterMaxDate.isSelected() && !valueMaxDate.getText().isBlank()) {
				try {
					maxDate = LocalDate.parse(valueMaxDate.getText()).plusDays(1).atStartOfDay(ZoneOffset.systemDefault()).toInstant().toEpochMilli() - 1;
				} catch (Exception e) {
					return FILTER_INVALID;
				}
			} else maxDate = null;
			if (searchQuery == null && min == null && max == null && minDate == null && maxDate == null)
				return FILTER_OFF;
			return tx -> {
				BigDecimal txDiff = ErgoInterface.toFullErg(totalReceived(tx, this.myAddresses) - totalSent(tx, this.myAddresses));
				if (min != null && txDiff.compareTo(min) < 0) return false;
				if (max != null && txDiff.compareTo(max) > 0) return false;
				if (minDate != null && tx.getTimestamp() < minDate) return false;
				if (maxDate != null && tx.getTimestamp() > maxDate) return false;
				if (searchQuery != null) {
					boolean idMatches = tx.getId().equals(searchQuery);
					if (!idMatches) {
						if (tx.getInputs().stream().noneMatch(input -> input.getAddress().equals(searchQuery))
							&& tx.getOutputs().stream().noneMatch(output -> output.getAddress().equals(searchQuery)))
							return false;
					}
				}
				return true;
			};
		}, search.textProperty(), filterMinErg.selectedProperty(), filterMaxErg.selectedProperty(), valueMinErg.textProperty(), valueMaxErg.textProperty(),
				filterMinDate.selectedProperty(), filterMaxDate.selectedProperty(), valueMinDate.textProperty(), valueMaxDate.textProperty()));

		filterPane.textProperty().bind(Bindings.createStringBinding(() -> {
			var predicate = shownTranslations.getPredicate();
			if (predicate == FILTER_OFF) return Main.lang("filter");
			if (predicate == FILTER_INVALID) return Main.lang("filter") + " (" + Main.lang("invalid") + ")";
			return Main.lang("filter") + " (" + Main.lang("active") + ")";
		}, shownTranslations.predicateProperty()));

		Stream.of(filterMinErg.selectedProperty(), filterMaxErg.selectedProperty(), valueMinErg.textProperty(), valueMaxErg.textProperty()).forEach(control -> {
			control.addListener(observable -> {

			});
		});

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
			container.getChildren().clear();
			Wallet wallet = Main.get().getWallet();
			// the wallet could have been closed before the transactions loaded
			if (wallet == null) return;
			if (transactions.isEmpty()) {
				container.getChildren().setAll(emptyHistory);
				return;
			}
			container.getChildren().setAll(scrollPane);
			myAddresses = wallet.addressStream().map(Address::toString).collect(Collectors.toUnmodifiableSet());
			allTransactions.setAll(transactions);
			Utils.fxRunDelayed(() -> scrollPane.scrollYToPixel(0), 50);
		});
		task.newThread();
		return task;
	}

	@FXML
	public void refresh() {
		container.getChildren().setAll(loadingLabel);
		refreshButton.disableProperty().bind(fetchHistory().runningProperty());
	}

	@FXML
	public void clearAllFilters(ActionEvent e) {
		Stream.of(search, filterMinErg, filterMaxErg, valueMinErg, valueMaxErg, filterMinDate, filterMaxDate, valueMinDate, valueMaxDate)
				.forEach(node -> {
					switch (node) {
						case CheckBox c -> c.setSelected(false);
						case TextField t -> t.clear();
						default -> {}
					}
				});
	}
}
