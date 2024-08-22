package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Translations;
import com.satergo.extra.market.PriceCurrency;
import com.satergo.extra.market.PriceSource;
import com.satergo.extra.ToggleSwitch;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.util.Pair;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsCtrl implements Initializable, WalletTab {

	@FXML private ToggleSwitch showPrice;
	@FXML private ComboBox<PriceSource> priceSource;
	@FXML private ComboBox<PriceCurrency> priceCurrency;
	@FXML private ComboBox<Translations.Entry> language;
	@FXML private ToggleSwitch requirePasswordForSending;

	@FXML private Button theme;
	@FXML private ImageView themeImage;
	@FXML private Label themeLabel;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		showPrice.selectedProperty().bindBidirectional(Main.programData().showPrice);
		priceSource.getItems().setAll(PriceSource.values());
		priceSource.getSelectionModel().select(0);
		priceSource.valueProperty().bindBidirectional(Main.programData().priceSource);
		priceCurrency.getItems().addAll(Main.programData().priceSource.get().supportedCurrencies);
		priceSource.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			PriceCurrency currentCurrency = priceCurrency.getValue();
			priceCurrency.getItems().setAll(newValue.supportedCurrencies);
			if (newValue.supportedCurrencies.contains(currentCurrency))
				priceCurrency.setValue(currentCurrency);
			else priceCurrency.getSelectionModel().select(0);
		});
		priceCurrency.valueProperty().bindBidirectional(Main.programData().priceCurrency);
		themeImage.imageProperty().bind(Bindings.when(Main.programData().lightTheme)
				.then(Load.image("/images/settings/sun.png"))
				.otherwise(Load.image("/images/settings/moon.png")));
		theme.textProperty().bind(Bindings.when(Main.programData().lightTheme)
				.then(Main.lang("darkTheme"))
				.otherwise(Main.lang("lightTheme")));
		// Screen reader users do not have to care about the theme
		theme.focusTraversableProperty().bind(Platform.accessibilityActiveProperty().not());
		theme.setOnAction(e -> Main.programData().lightTheme.set(!Main.programData().lightTheme.get()));
		language.getItems().addAll(Main.get().translations.getEntries());
		language.setValue(Main.get().translations.getEntry(Main.programData().language.get()));
		language.valueProperty().addListener((observable, oldValue, newValue) -> {
			Main.get().setLanguage(newValue);
			Main.get().getWalletPage().cancelRepeatingTasks();
			WalletCtrl walletCtrl = new WalletCtrl("settings");
			Main.get().displayWalletPage(new Pair<>(Load.fxmlControllerFactory("/wallet.fxml", walletCtrl), walletCtrl));
		});
		language.setConverter(Translations.Entry.TO_NAME_CONVERTER);
		requirePasswordForSending.selectedProperty().bindBidirectional(Main.programData().requirePasswordForSending);
	}
}
