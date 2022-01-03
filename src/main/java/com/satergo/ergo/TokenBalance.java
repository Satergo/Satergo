package com.satergo.ergo;

public record TokenBalance(String id, long amount, int decimals, String name) implements Comparable<TokenBalance> {

	public TokenBalance withAmount(long amount) {
		return new TokenBalance(this.id, amount, this.decimals, this.name);
	}
	
	public static TokenBalance sum(TokenBalance tb1, TokenBalance tb2) {
		if (!tb1.id.equals(tb2.id)) throw new IllegalArgumentException();
		return tb1.withAmount(tb1.amount + tb2.amount);
	}

	@Override
	public int compareTo(TokenBalance o) {
		return name.compareTo(o.name);
	}
}
