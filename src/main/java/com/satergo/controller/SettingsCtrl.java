package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Translations;
import com.satergo.extra.CommonCurrency;
import com.satergo.extra.PriceSource;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.util.Pair;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsCtrl implements Initializable, WalletTab {

	@FXML private CheckBox showPrice;
	@FXML private ComboBox<PriceSource> priceSource;
	@FXML private ComboBox<CommonCurrency> priceCurrency;
	@FXML private CheckBox lightTheme;
	@FXML private ComboBox<Translations.Entry> language;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		showPrice.selectedProperty().bindBidirectional(Main.programData().showPrice);
		priceSource.getItems().setAll(PriceSource.values());
		priceSource.getSelectionModel().select(0);
		priceSource.valueProperty().bindBidirectional(Main.programData().priceSource);
		priceCurrency.getItems().addAll(Main.programData().priceSource.get().supportedCurrencies);
		priceSource.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			CommonCurrency currentCurrency = priceCurrency.getValue();
			priceCurrency.getItems().clear();
			priceCurrency.getItems().addAll(newValue.supportedCurrencies);
			if (newValue.supportedCurrencies.contains(currentCurrency))
				priceCurrency.setValue(currentCurrency);
			else priceCurrency.getSelectionModel().select(0);
		});
		priceCurrency.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			Main.programData().priceCurrency.set(newValue);
		});
		priceCurrency.valueProperty().bindBidirectional(Main.programData().priceCurrency);
		lightTheme.selectedProperty().bindBidirectional(Main.programData().lightTheme);
		language.getItems().addAll(Main.get().translations.getEntries());
		language.setValue(Main.get().translations.getEntry(Main.programData().language.get()));
		language.valueProperty().addListener((observable, oldValue, newValue) -> {
			Main.get().setLanguage(newValue);
			Main.get().getWalletPage().cancelTimer();
			WalletCtrl walletCtrl = new WalletCtrl("settings");
			Main.get().displayWalletPage(new Pair<>(Load.fxmlControllerFactory("/wallet.fxml", walletCtrl), walletCtrl));
		});
		language.setConverter(Translations.Entry.TO_NAME_CONVERTER);
	}
}
