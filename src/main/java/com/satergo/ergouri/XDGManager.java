package com.satergo.ergouri;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

class XDGManager implements ErgoURIManager {

	private static final Path HOME = Path.of(System.getProperty("user.home"));
	private static final Path APPLICATIONS_PATH = Path.of(System.getProperty("user.home")).resolve(".local/share/applications");
	private static final Path[] POSSIBLE_MIMEAPPS_LISTS = {
			HOME.resolve(".config/gnome-mimeapps.list"),
			HOME.resolve(".config/mimeapps.list"),
			HOME.resolve(".local/share/applications/gnome-mimeapps.list"),
			HOME.resolve(".local/share/applications/mimeapps.list"),
			Path.of("/etc/xdg/gnome-mimeapps.list"),
			Path.of("/etc/xdg/mimeapps.list"),
			Path.of("/usr/local/share/applications/gnome-mimeapps.list"),
			Path.of("/usr/local/share/applications/mimeapps.list"),
			Path.of("/usr/share/applications/gnome-mimeapps.list"),
			Path.of("/usr/share/applications/mimeapps.list")
	};
	private static final String FILE_NAME = "ergo-url.desktop";

	private final Path mimeappsList;

	public XDGManager() {
		if (!Files.exists(APPLICATIONS_PATH)) throw new IllegalStateException();
		mimeappsList = Arrays.stream(POSSIBLE_MIMEAPPS_LISTS).filter(Files::exists).findFirst().orElseThrow(IllegalStateException::new);
	}

	@Override
	public void register() throws IOException {
		if (ErgoURIManager.getExecutablePath() == null) return;
		// create .desktop file; todo icon
		Files.writeString(APPLICATIONS_PATH.resolve(FILE_NAME), """
				[Desktop Entry]
				Type=Application
				Name=Satergo
				Exec=%s
				StartupNotify=false
				MimeType=x-scheme-handler/ergo;
				NoDisplay=true""".formatted("\"" + ErgoURIManager.getExecutablePath() + "\" --uri %u"));
		// register it
		Runtime.getRuntime().exec(new String[]{"xdg-mime", "default", FILE_NAME, "x-scheme-handler/ergo"});
	}

	@Override
	public void unregister() throws IOException {
		// delete .desktop file
		if (Files.deleteIfExists(APPLICATIONS_PATH.resolve(FILE_NAME))) {
			// unregister it
			ArrayList<String> lines = Files.lines(mimeappsList).collect(Collectors.toCollection(ArrayList::new));
			lines.remove("x-scheme-handler/erg=" + FILE_NAME);
			Files.write(mimeappsList, lines);
		}
	}

	@Override
	public boolean isRegistered() {
		return Files.exists(APPLICATIONS_PATH.resolve(FILE_NAME));
	}
}
