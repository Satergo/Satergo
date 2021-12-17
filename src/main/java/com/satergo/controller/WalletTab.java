package com.satergo.controller;

public interface WalletTab {

	/**
	 * Called when the wallet tab is changed to something else
	 */
	default void cleanup() {}
}
