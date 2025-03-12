package com.satergo.extra;

public  class UnsupportedFormatVersionException extends UnsupportedOperationException {
	public final long formatVersion;
	public UnsupportedFormatVersionException(String message, long formatVersion) {
		super(message);
		this.formatVersion = formatVersion;
	}
}