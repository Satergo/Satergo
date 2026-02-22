package com.satergo;

import com.satergo.ergo.TokenBalance;
import com.satergo.extra.market.PriceCurrency;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * AllDecimals: all decimals are shown, even if the trailing ones are zeroes
 * Exact: as many decimals as needed to display the exact number. no trailing zeroes.
 */
public class FormatNumber {

	private static ThreadLocal<DecimalFormat> ergAllDecimals, ergExact;

	static {
		update();
	}

	public static String integer(int integer) {
		return NumberFormat.getInstance().format(integer);
	}

	public static String ergAllDecimals(BigDecimal erg) {
		return ergAllDecimals.get().format(erg);
	}

	public static String ergExact(BigDecimal erg) {
		return ergExact.get().format(erg);
	}

	public static String tokenExact(TokenBalance tokenBalance) {
		DecimalFormat df = new DecimalFormat("0");
		df.setMaximumFractionDigits(tokenBalance.decimals());
		return df.format(tokenBalance.fullAmount());
	}

	public static String currencyExact(BigDecimal value, PriceCurrency currency) {
		DecimalFormat df = new DecimalFormat("0");
		df.setMaximumFractionDigits(currency.displayDecimals);
		return df.format(value);
	}

	/**
	 * When the default Locale is updated, this needs to be called to reselect the correct symbols for the new Locale
	 */
	public static synchronized void update() {
		if (ergAllDecimals != null) ergAllDecimals.remove();
		if (ergExact != null) ergExact.remove();
		ergAllDecimals = ThreadLocal.withInitial(() -> new DecimalFormat("0.000000000"));
		ergExact = ThreadLocal.withInitial(() -> new DecimalFormat("0.#########"));
	}
}
