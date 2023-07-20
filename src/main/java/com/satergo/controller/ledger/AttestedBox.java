package com.satergo.controller.ledger;

import com.satergo.jledger.protocol.ergo.ErgoResponse;
import org.ergoplatform.ErgoBox;

import java.util.Objects;

public record AttestedBox(ErgoBox box, ErgoResponse.AttestedBoxFrame[] frames, byte[] extension) {
	public AttestedBox {
		Objects.requireNonNull(box, "box");
		Objects.requireNonNull(frames, "frames");
		Objects.requireNonNull(extension, "extension");
	}
}
