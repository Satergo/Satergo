package com.satergo.ergopay;

import org.ergoplatform.appkit.ReducedTransaction;

public record ErgoPayTransactRequest(ReducedTransaction reducedTx) implements ErgoPayRequest {
}
