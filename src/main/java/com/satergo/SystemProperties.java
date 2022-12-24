package com.satergo;

import java.util.Optional;

import static java.lang.System.getProperty;

public class SystemProperties {

	public static Optional<String> mainnetExplorerApi() {
		return Optional.ofNullable(getProperty("satergo.mainnetExplorerApi"));
	}

	public static Optional<String> testnetExplorerApi() {
		return Optional.ofNullable(getProperty("satergo.testnetExplorerApi"));
	}

	public static boolean rawSeedPhrase() {
		return Boolean.getBoolean("satergo.rawSeedPhrase");
	}

	public static boolean alwaysNonstandardDerivation() {
		return Boolean.getBoolean("satergo.alwaysNonstandardDerivation");
	}

}
