package com.satergo.ergo;

public interface TokenSummary extends Comparable<TokenSummary> {

	String id();
	int decimals();
	String name();

	@Override
	default int compareTo(TokenSummary o) {
		return name().compareTo(o.name());
	}
}
