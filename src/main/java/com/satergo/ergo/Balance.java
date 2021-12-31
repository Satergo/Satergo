package com.satergo.ergo;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Balance(long confirmed, long unconfirmed, List<TokenBalance> confirmedTokens,
					  List<TokenBalance> unconfirmedTokens) {

	public static Balance combine(Balance bal1, Balance bal2) {
		return new Balance(bal1.confirmed + bal2.confirmed, bal1.unconfirmed + bal2.unconfirmed,
				List.copyOf(Stream.concat(bal1.confirmedTokens.stream(), bal2.confirmedTokens.stream()).collect(Collectors.toMap(TokenBalance::id, Function.identity(), TokenBalance::sum)).values()),
				List.copyOf(Stream.concat(bal1.unconfirmedTokens.stream(), bal2.unconfirmedTokens.stream()).collect(Collectors.toMap(TokenBalance::id, Function.identity(), TokenBalance::sum)).values()));
	}
}
