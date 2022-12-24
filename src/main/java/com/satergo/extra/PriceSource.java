package com.satergo.extra;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.Utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum PriceSource {
	KUCOIN(CommonCurrency.USD, CommonCurrency.EUR, CommonCurrency.BTC) {
		@Override
		protected BigDecimal fetchPriceInternal(CommonCurrency commonCurrency) throws IOException {
			HttpClient httpClient = HttpClient.newHttpClient();
			if (commonCurrency == CommonCurrency.USD || commonCurrency == CommonCurrency.EUR) {
				HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.kucoin.com/api/v1/prices?base=" + commonCurrency.uc() + "&currencies=ERG")).build();
				try {
					JsonObject response = JsonParser.object().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
					return new BigDecimal(response.getObject("data").getString("ERG"));
				} catch (JsonParserException | InterruptedException e) {
					throw new RuntimeException(e);
				}
			} else if (commonCurrency == CommonCurrency.BTC) {
				HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.kucoin.com/api/v1/market/orderbook/level1?symbol=ERG-BTC")).build();
				try {
					JsonObject response = JsonParser.object().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
					return new BigDecimal(response.getObject("data").getString("price"));
				} catch (JsonParserException | InterruptedException e) {
					throw new RuntimeException(e);
				}
			} else throw new IllegalArgumentException("unsupported common currency");
		}
	},
	COINGECKO(CommonCurrency.USD, CommonCurrency.EUR, CommonCurrency.BTC) {
		@Override
		protected BigDecimal fetchPriceInternal(CommonCurrency commonCurrency) throws IOException {
			if (!supportedCurrencies.contains(commonCurrency)) throw new IllegalArgumentException("unsupported common currency");
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=ergo&vs_currencies=" + commonCurrency.lc())).build();
			try {
				JsonObject response = JsonParser.object().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
				return BigDecimal.valueOf(response.getObject("ergo").getDouble(commonCurrency.lc()));
			} catch (JsonParserException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	},
	COINEX(CommonCurrency.USD, CommonCurrency.BTC) {
		@Override
		protected BigDecimal fetchPriceInternal(CommonCurrency commonCurrency) throws IOException {
			if (!supportedCurrencies.contains(commonCurrency)) throw new IllegalArgumentException("unsupported common currency");
			String crypto = commonCurrency == CommonCurrency.USD ? "USDT" : "BTC";
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.coinex.com/v1/market/ticker?market=ERG" + crypto)).build();
			try {
				JsonObject response = JsonParser.object().from(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
				return new BigDecimal(response.getObject("data").getObject("ticker").getString("last"));
			} catch (JsonParserException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	};

	public final List<CommonCurrency> supportedCurrencies;

	PriceSource(CommonCurrency... supportedCurrencies) {
		if (Arrays.stream(supportedCurrencies).anyMatch(c -> c == CommonCurrency.BTC)) {
			ArrayList<CommonCurrency> c = new ArrayList<>();
			Collections.addAll(c, supportedCurrencies);
			c.add(CommonCurrency.SAT);
			this.supportedCurrencies = Collections.unmodifiableList(c);
		} else this.supportedCurrencies = List.of(supportedCurrencies);
	}

	protected abstract BigDecimal fetchPriceInternal(CommonCurrency commonCurrency) throws IOException;

	public final BigDecimal fetchPrice(CommonCurrency commonCurrency) throws IOException {
		if (commonCurrency == CommonCurrency.SAT) {
			if (!supportedCurrencies.contains(CommonCurrency.BTC)) throw new IllegalArgumentException("unsupported common currency");
			return fetchPriceInternal(CommonCurrency.BTC).movePointRight(8);
		}
		if (!supportedCurrencies.contains(commonCurrency)) throw new IllegalArgumentException("unsupported common currency " + commonCurrency + " " + this);
		return fetchPriceInternal(commonCurrency);
	}
}
