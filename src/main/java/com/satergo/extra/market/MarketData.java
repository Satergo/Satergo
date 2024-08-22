package com.satergo.extra.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.satergo.Utils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;

public class MarketData {

	// The current value of 1 ERG, in the user's chosen currency.
	public final SimpleObjectProperty<BigDecimal> ergValue = new SimpleObjectProperty<>();
	public final ObservableList<TokenPrice> tokenPrices = FXCollections.observableArrayList();

	public void updateTokenPrices() throws IOException, InterruptedException {
		try {
			List<TokenPrice> prices = fetchErgoDexTokenPrices();
			Utils.runLaterOrNow(() -> tokenPrices.setAll(prices));
		} catch (IOException | InterruptedException e) {
			tokenPrices.clear();
			throw e;
		}
	}

	/** Can return null */
	public BigDecimal tokensPerErg(String tokenId) {
		return tokenPrices.stream().filter(p -> p.baseId.equals("0".repeat(64)) && p.quoteId.equals(tokenId)).findAny().map(TokenPrice::lastPrice).orElse(null);
	}

	public BigDecimal ergPriceOfToken(String tokenId) {
		BigDecimal tokensPerErg = tokensPerErg(tokenId);
		return tokensPerErg == null ? null : BigDecimal.ONE.divide(tokensPerErg, new MathContext(9, RoundingMode.HALF_EVEN));
	}


	public record TokenPrice(String id, String baseId, String baseSymbol, String quoteId, BigDecimal lastPrice) {
	}

	public static List<TokenPrice> fetchErgoDexTokenPrices() throws IOException, InterruptedException {
		HttpResponse<String> response = Utils.HTTP.send(Utils.httpRequestBuilder().uri(URI.create("https://api.spectrum.fi/v1/price-tracking/markets")).build(), HttpResponse.BodyHandlers.ofString());
		return new Gson().fromJson(response.body(), TypeToken.getParameterized(List.class, TokenPrice.class).getType());
	}
}
