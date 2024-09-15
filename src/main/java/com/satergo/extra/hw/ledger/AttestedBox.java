package com.satergo.extra.hw.ledger;

import com.satergo.jledger.protocol.ergo.ErgoResponse;

import java.util.Objects;

public record AttestedBox(ErgoResponse.AttestedBoxFrame[] frames, byte[] extension) {
	public AttestedBox {
		Objects.requireNonNull(frames, "frames");
		Objects.requireNonNull(extension, "extension");
	}
}
