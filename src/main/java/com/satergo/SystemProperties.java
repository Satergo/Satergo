package com.satergo;

import java.nio.file.Path;
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

	public static Optional<String> ledgerEmulator() {
		return Optional.ofNullable(getProperty("satergo.ledgerEmulator"));
	}
	public static Optional<Integer> ledgerEmulatorPort() {
		return Optional.ofNullable(Integer.getInteger("satergo.ledgerEmulatorPort"));
	}

	public static String packagePlatform() {
		return System.getProperty("satergo.packagePlatform", "DEV");
	}

	public enum PackageType {
		PORTABLE, INSTALLATION;
	}
	public static final PackageType FALLBACK_PACKAGE_TYPE = PackageType.PORTABLE;

	public static PackageType packageType() {
		PackageType packageType = nullablePackageType();
		return packageType == null ? FALLBACK_PACKAGE_TYPE : packageType;
	}

	public static PackageType nullablePackageType() {
		return System.getProperties().containsKey("satergo.packageType")
				? PackageType.valueOf(System.getProperty("satergo.packageType"))
				: null;
	}

	public static Path appHome() {
		return System.getProperties().containsKey("satergo.appHome")
				? Path.of(System.getProperty("satergo.appHome"))
				: Path.of(System.getProperty("user.dir"));
	}
}
