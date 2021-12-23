package com.satergo.ergo;

import org.ergoplatform.appkit.ErgoId;

public record TokenInfo(ErgoId id, int decimals, String name) implements Comparable<TokenInfo> {

	@Override
	public String toString() {
		return name + " (" + id.toString().substring(0, 20) + "...)";
	}

	@Override
	public int compareTo(TokenInfo o) {
		return name.compareTo(o.name);
	}
}
