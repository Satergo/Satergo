package com.satergo.ergouri;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public interface ErgoURIManager {

	static Path getExecutablePath() {
		String property = System.getProperty("satergo.launcher");
		if (property == null) {
			System.out.println("[note] ErgoURI could not find the launcher executable, this is probably a development environment");
			return null;
		}
		return Path.of(property).normalize();
	}

	void register() throws IOException;
	void unregister() throws IOException;
	boolean isRegistered();
	boolean actionRequired();

	static Optional<ErgoURIManager> getForPlatform() {
		String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
		if (osName.startsWith("windows")) {
			return Optional.of(new WindowsManager());
		} else if (osName.startsWith("linux")) {
			if (System.getenv("XDG_SESSION_TYPE") != null) {
				return Optional.of(new XDGManager());
			}
			return Optional.empty();
		} else if (osName.contains("mac") || osName.contains("darwin")) {
			return Optional.empty();
		}
		return Optional.empty();
	}
}
