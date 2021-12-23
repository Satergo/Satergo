package com.satergo.ergouri;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public interface ErgoURIManager {

	static Path getExecutablePath() {
		String property = System.getProperty("satergo.launcher");
		if (property == null) {
			System.out.println("[note] ErgoURI could not find the launcher executable, this is probably a development environment");
			return null;
		}
		return Path.of(property);
	}

	void register() throws IOException;
	void unregister() throws IOException;
	boolean isRegistered();

	static ErgoURIManager getForPlatform() {
		String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
		if (osName.startsWith("windows")) {
			return new WindowsManager();
		} else if (osName.startsWith("linux")) {
			if (System.getenv("XDG_SESSION_TYPE") != null) {
				return new XDGManager();
			}
			return null;
		} else if (osName.contains("mac") || osName.contains("darwin")) {
			return null;
		}
		return null;
	}
}
