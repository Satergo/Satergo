package com.satergo;

import java.io.IOException;
import java.util.Optional;
import java.util.jar.Manifest;

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

	public enum PackageType {
		PORTABLE, INSTALLATION;

		public static final PackageType FALLBACK = PORTABLE;
	}

	private static Manifest readManifest() {
		try {
			return new Manifest(SystemProperties.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
		} catch (IOException e) {
			return null;
		}
	}
	private static final Manifest manifest = readManifest();

	public static PackageType packageType() {
		if (manifest == null || !manifest.getMainAttributes().containsKey("Satergo-Package-Type"))
			return PackageType.FALLBACK;
		return PackageType.valueOf(manifest.getMainAttributes().getValue("Satergo-Package-Type"));
	}

	public static String packagePlatform() {
		if (manifest == null || !manifest.getMainAttributes().containsKey("Satergo-Package-Platform"))
			return "src";
		return manifest.getMainAttributes().getValue("Satergo-Package-Platform");
	}
}
