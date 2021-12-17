package com.satergo.ergo;

import java.util.List;

public record Balance(long confirmed, long unconfirmed, List<TokenBalance> confirmedTokens,
					  List<TokenBalance> unconfirmedTokens) {
}
