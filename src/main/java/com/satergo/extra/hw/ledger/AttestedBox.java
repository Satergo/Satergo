package com.satergo.extra.hw.ledger;

import com.satergo.jledger.protocol.ergo.ErgoResponse;
import org.ergoplatform.appkit.InputBox;

import java.util.Objects;

public record AttestedBox(InputBox box, ErgoResponse.AttestedBoxFrame[] frames, byte[] extension) {
	public AttestedBox {
		Objects.requireNonNull(box, "box");
		Objects.requireNonNull(frames, "frames");
		Objects.requireNonNull(extension, "extension");
	}
}
