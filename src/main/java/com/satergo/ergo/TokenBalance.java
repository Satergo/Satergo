package com.satergo.ergo;

import java.math.BigDecimal;

public record TokenBalance(String id, long amount, int decimals, String name) implements TokenSummary {

	public TokenBalance withAmount(long amount) {
		return new TokenBalance(this.id, amount, this.decimals, this.name);
	}
	
	public static TokenBalance sum(TokenBalance tb1, TokenBalance tb2) {
		if (!tb1.id.equals(tb2.id)) throw new IllegalArgumentException();
		return tb1.withAmount(tb1.amount + tb2.amount);
	}

	public BigDecimal fullAmount() {
		return ErgoInterface.fullTokenAmount(amount, decimals);
	}
}
