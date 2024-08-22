package com.satergo.extra.market;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.Utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;

import static com.satergo.Utils.HTTP;

public enum PriceSource {
	KUCOIN(PriceCurrency.USD, PriceCurrency.EUR, PriceCurrency.BTC, PriceCurrency.SAT) {
		@Override
		protected BigDecimal fetchPriceInternal(PriceCurrency priceCurrency) throws IOException {
			if (priceCurrency == PriceCurrency.USD || priceCurrency == PriceCurrency.EUR) {
				HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.kucoin.com/api/v1/prices?base=" + priceCurrency.uc() + "&currencies=ERG")).timeout(Duration.ofSeconds(10)).build();
				try {
					JsonObject response = JsonParser.object().from(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body());
					return new BigDecimal(response.getObject("data").getString("ERG"));
				} catch (JsonParserException | InterruptedException e) {
					throw new RuntimeException(e);
				}
			} else if (priceCurrency == PriceCurrency.BTC) {
				HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.kucoin.com/api/v1/market/orderbook/level1?symbol=ERG-BTC")).build();
				try {
					JsonObject response = JsonParser.object().from(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body());
					return new BigDecimal(response.getObject("data").getString("price"));
				} catch (JsonParserException | InterruptedException e) {
					throw new RuntimeException(e);
				}
			} else throw new IllegalArgumentException("unsupported price currency");
		}
	},
	COINGECKO(Stream.concat(Stream.of(PriceCurrency.BTC, PriceCurrency.SAT), Stream.of("USD", "AED", "ARS", "AUD", "BDT", "BHD", "BMD", "BRL", "CAD", "CHF", "CLP", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "JPY", "KRW", "KWD", "LKR", "MMK", "MXN", "MYR", "NGN", "NOK", "NZD", "PHP", "PKR", "PLN", "RUB", "SAR", "SEK", "SGD", "THB", "TRY", "TWD", "UAH", "VEF", "VND", "ZAR")
			.map(Currency::getInstance).map(PriceCurrency::fiat)).toList()) {
		@Override
		protected BigDecimal fetchPriceInternal(PriceCurrency priceCurrency) throws IOException {
			if (!supportedCurrencies.contains(priceCurrency)) throw new IllegalArgumentException("unsupported price currency");
			HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=ergo&vs_currencies=" + priceCurrency.lc())).timeout(Duration.ofSeconds(10)).build();
			try {
				JsonObject response = JsonParser.object().from(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body());
				return BigDecimal.valueOf(response.getObject("ergo").getDouble(priceCurrency.lc()));
			} catch (JsonParserException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	},
	COINEX(PriceCurrency.USD, PriceCurrency.BTC, PriceCurrency.SAT) {
		@Override
		protected BigDecimal fetchPriceInternal(PriceCurrency priceCurrency) throws IOException {
			if (!supportedCurrencies.contains(priceCurrency)) throw new IllegalArgumentException("unsupported price currency");
			String crypto = priceCurrency == PriceCurrency.USD ? "USDT" : "BTC";
			HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.coinex.com/v1/market/ticker?market=ERG" + crypto)).timeout(Duration.ofSeconds(10)).build();
			try {
				JsonObject response = JsonParser.object().from(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body());
				return new BigDecimal(response.getObject("data").getObject("ticker").getString("last"));
			} catch (JsonParserException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	};

	public static final PriceSource DEFAULT = COINGECKO;

	public final List<PriceCurrency> supportedCurrencies;

	PriceSource(PriceCurrency... supportedCurrencies) {
		this(Collections.unmodifiableList(Arrays.asList(supportedCurrencies)));
	}

	PriceSource(List<PriceCurrency> supportedCurrencies) {
		this.supportedCurrencies = supportedCurrencies;
	}

	protected abstract BigDecimal fetchPriceInternal(PriceCurrency priceCurrency) throws IOException;

	public final BigDecimal fetchPrice(PriceCurrency priceCurrency) throws IOException {
		if (priceCurrency == PriceCurrency.SAT) {
			if (!supportedCurrencies.contains(PriceCurrency.BTC)) throw new IllegalArgumentException("unsupported price currency");
			return fetchPriceInternal(PriceCurrency.BTC).movePointRight(8);
		}
		if (!supportedCurrencies.contains(priceCurrency)) throw new IllegalArgumentException("unsupported price currency " + priceCurrency + " " + this);
		return fetchPriceInternal(priceCurrency);
	}
}
