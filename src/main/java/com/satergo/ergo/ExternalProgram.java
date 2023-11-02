package com.satergo.ergo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ExternalProgram {

	static Path findJavaBinary() {
		Path javaInstallation = Path.of(System.getProperty("java.home"));
		Path binDirectory = javaInstallation.resolve("bin");
		if (Files.exists(binDirectory.resolve("java.exe")))
			return binDirectory.resolve("java.exe");
		return binDirectory.resolve("java");
	}

	void start() throws IOException;
	InputStream standardOutput();
	boolean isRunning();
	void stop();
	void waitForExit();
	long startedTime();
}
