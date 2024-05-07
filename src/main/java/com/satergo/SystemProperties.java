package com.satergo;

import java.io.IOException;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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

	// Manifest

	public enum PackageType {
		PORTABLE, INSTALLATION;

		public static final PackageType FALLBACK = PORTABLE;
	}

	private static final Attributes manifestSection;
	private static final Attributes.Name
			PACKAGE_TYPE = new Attributes.Name("Package-Type"),
			PACKAGE_PLATFORM = new Attributes.Name("Package-Platform");

	static {
		Manifest manifest = null;
		try {
			manifest = new Manifest(SystemProperties.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
		} catch (IOException ignored) {
		}
		manifestSection = manifest == null ? null : manifest.getAttributes("Satergo");
	}

	public static PackageType packageType() {
		return manifestSection == null
				? PackageType.FALLBACK
				: PackageType.valueOf(manifestSection.getValue(PACKAGE_TYPE));
	}

	public static String packagePlatform() {
		return manifestSection == null
				? "src"
				: manifestSection.getValue(PACKAGE_PLATFORM);
	}
}
