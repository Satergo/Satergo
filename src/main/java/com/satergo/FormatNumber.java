package com.satergo;

import com.satergo.ergo.ErgoInterface;
import com.satergo.ergo.TokenBalance;
import com.satergo.extra.PriceCurrency;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * AllDecimals: all decimals are shown, even if the trailing ones are zeroes
 * Exact: as many decimals as needed to display the exact number. no trailing zeroes.
 */
public class FormatNumber {

	private static DecimalFormat ergAllDecimals, ergExact;

	static {
		update();
	}

	public static String ergAllDecimals(BigDecimal erg) {
		return ergAllDecimals.format(erg);
	}

	public static String ergExact(BigDecimal erg) {
		return ergExact.format(erg);
	}

	public static String tokenExact(TokenBalance tokenBalance) {
		DecimalFormat df = new DecimalFormat("0");
		df.setMaximumFractionDigits(tokenBalance.decimals());
		return df.format(ErgoInterface.fullTokenAmount(tokenBalance.amount(), tokenBalance.decimals()));
	}

	public static String currencyExact(BigDecimal value, PriceCurrency currency) {
		DecimalFormat df = new DecimalFormat("0");
		df.setMaximumFractionDigits(currency.displayDecimals);
		return df.format(value);
	}

	/**
	 * When the default Locale is updated, this needs to be called to reselect the correct symbols for the new Locale
	 */
	public static void update() {
		ergAllDecimals = new DecimalFormat("0.000000000");
		ergExact = new DecimalFormat("0.#########");
	}
}
