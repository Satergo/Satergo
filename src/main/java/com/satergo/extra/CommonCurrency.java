package com.satergo.extra;

import java.util.Locale;

public enum CommonCurrency {
	USD(2), EUR(2), BTC(6), SAT(0);

	public final int displayDecimals;

	CommonCurrency(int displayDecimals) {
		this.displayDecimals = displayDecimals;
	}

	public String lc() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String uc() {
		return name();
	}
}
