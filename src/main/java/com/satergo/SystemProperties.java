package com.satergo;

import java.util.Optional;

import static java.lang.System.getProperty;

public class SystemProperties {

	// System properties

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

	public static String packagePlatform() {
		return System.getProperty("satergo.packagePlatform", "DEV");
	}

	public enum PackageType {
		PORTABLE, INSTALLATION;
	}
	public static final PackageType FALLBACK_PACKAGE_TYPE = PackageType.PORTABLE;

	public static PackageType packageType() {
		return System.getProperties().contains("satergo.packageType")
				? PackageType.valueOf(System.getProperty("satergo.packageType"))
				: FALLBACK_PACKAGE_TYPE;
	}
}
