package com.satergo.extra.market;

import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;

/**
 * Identity comparison is safe
 */
public class PriceCurrency {

	private static final HashMap<Currency, PriceCurrency> FIATS = new HashMap<>();
	private static final HashMap<String, PriceCurrency> CRYPTOS = new HashMap<>();

	public static final PriceCurrency
			USD = PriceCurrency.fiat(Currency.getInstance("USD")),
			EUR = PriceCurrency.fiat(Currency.getInstance("EUR")),
			BTC = PriceCurrency.crypto(6, "BTC"),
			SAT = PriceCurrency.crypto(0, "SAT");

	public final boolean fiat;
	public final int displayDecimals;
	private final String codeUpper, codeLower;

	private PriceCurrency(Currency fiatCurrency) {
		this.fiat = true;
		this.displayDecimals = fiatCurrency.getDefaultFractionDigits();
		this.codeUpper = fiatCurrency.getCurrencyCode();
		this.codeLower = fiatCurrency.getCurrencyCode().toLowerCase(Locale.ROOT);
	}

	private PriceCurrency(int displayDecimals, String code) {
		this.fiat = false;
		this.displayDecimals = displayDecimals;
		this.codeUpper = code.toUpperCase(Locale.ROOT);
		this.codeLower = code.toLowerCase(Locale.ROOT);
	}

	public static PriceCurrency get(String code) {
		if (!code.toUpperCase(Locale.ROOT).equals(code))
			throw new IllegalArgumentException("Code must be uppercase");
		if (CRYPTOS.containsKey(code))
			return CRYPTOS.get(code);
		try {
			return FIATS.get(Currency.getInstance(code));
		} catch (Exception e) {
			return null;
		}
	}

	public static PriceCurrency fiat(Currency currency) {
		return FIATS.computeIfAbsent(currency, PriceCurrency::new);
	}

	public static PriceCurrency crypto(int displayDecimals, String code) {
		if (!code.toUpperCase(Locale.ROOT).equals(code))
			throw new IllegalArgumentException("Code must be uppercase");
		return CRYPTOS.computeIfAbsent(code, k -> new PriceCurrency(displayDecimals, code));
	}

	public String uc() { return codeUpper; }
	public String lc() { return codeLower; }

	@Override
	public String toString() {
		return codeUpper;
	}
}
