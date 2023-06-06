package com.satergo.ergopay;

import org.ergoplatform.appkit.ReducedTransaction;

public sealed interface ErgoPayRequest permits ErgoPaySignRequest, ErgoPayTransactRequest {

	ReducedTransaction reducedTx();
}
