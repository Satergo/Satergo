package com.satergo.extra;

import java.util.Objects;

public class EncryptedData {

	public final byte[] salt, iv, data;

	public EncryptedData(byte[] salt, byte[] iv, byte[] data) {
		this.salt = Objects.requireNonNull(salt);
		this.iv = Objects.requireNonNull(iv);
		this.data = Objects.requireNonNull(data);
	}
}
