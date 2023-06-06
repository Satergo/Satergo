package com.satergo.ergopay;

import com.google.gson.annotations.SerializedName;
import org.ergoplatform.appkit.ReducedTransaction;

public record ErgoPaySignRequest(@SerializedName("reducedTx") ReducedTransaction reducedTx,
								 @SerializedName("address") String address,
								 @SerializedName("message") String message,
								 @SerializedName("messageSeverity") MessageSeverity messageSeverity,
								 @SerializedName("replyTo") String replyTo) implements ErgoPayRequest {

	public enum MessageSeverity {
		INFORMATION, SECURITY, ERROR
	}
}
