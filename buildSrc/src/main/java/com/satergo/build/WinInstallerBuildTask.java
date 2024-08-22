package com.satergo.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.spi.ToolProvider;

public class WinInstallerBuildTask extends DefaultTask {

	@TaskAction
	public void execute() throws TaskExecutionException, IOException {
		WinInstallerBuildExt ext = getProject().getExtensions().getByType(WinInstallerBuildExt.class);

		File mainJar = (File) getProject().getTasks().getByPath("satergoRuntime").getExtensions().getByName("mainJar");

		Path winInstallerInput = getBuildDirPath().resolve("tmp/win-installer-input");
		if (Files.exists(winInstallerInput))
			FileUtils.deleteDirectory(winInstallerInput);

		Files.createDirectories(winInstallerInput);
		Files.copy(mainJar.toPath(), winInstallerInput.resolve(mainJar.getName()));
//		Files.copy(ext.icoIconPath, winInstallerInput.resolve("icon.ico"));

		Args args = new Args();
		args.add("type", ext.installerType.toString().toLowerCase(Locale.ROOT));

		// Package information
		args.add("name", ext.appName);
		args.add("app-version", ext.appVersion);
		args.add("icon", ext.icoIconPath.toAbsolutePath().toString());
		args.add("vendor", ext.vendor);
		// copyright
		args.add("description", ext.description);
		// description

		args.add("dest", ext.outputDir.toAbsolutePath().toString());

		// Runtime image options
		args.add("runtime-image", getBuildDirPath()
				.resolve(RuntimeBuildExt.runtimeDirectoryName).toAbsolutePath().toString());
		// Application launcher
		args.add("input", winInstallerInput.toAbsolutePath().toString());
		args.add("main-jar", mainJar.getName());

		args.add("about-url", ext.aboutUrl);
		for (Path fileAssociation : ext.fileAssociations) {
			args.add("file-associations", fileAssociation.toAbsolutePath().toString());
		}
		// license

		// Windows-specific

		// Start menu entry
		args.add("win-menu");
		// Install to C:\Users\name\AppData\Local instead of C:\Program Files
		// This makes it so no UAC prompt is shown (as no admin privileges are needed),
		// and that updates can be performed without admin privileges
		args.add("win-per-user-install");
		// Desktop shortcut
		args.add("win-shortcut");
		// URL that has information regarding updates
		args.add("win-update-url", ext.updateUrl);
		// A unique ID that will let the OS know that upgrade installers are related to this application
		args.add("win-upgrade-uuid", ext.windowsUpgradeUUID);

		StringWriter err = new StringWriter();
		ToolProvider jpackage = ToolProvider.findFirst("jpackage").orElseThrow();
		ArrayList<String> allArgs = new ArrayList<>();
		Collections.addAll(allArgs, args.toArray());
		allArgs.addAll(ext.extraArgs);
		int result = jpackage.run(new PrintWriter(System.out), new PrintWriter(err), allArgs.toArray(new String[0]));
		if (result != 0)
			throw new RuntimeException("Executing jpackage failed, exited with " + result + ". " + err);
	}

	private Path getBuildDirPath() {
		return getProject().getLayout().getBuildDirectory().get().getAsFile().toPath();
	}

	private static class Args {
		private record Arg(String key, String value) {}

		private final ArrayList<Arg> args = new ArrayList<>();
		/** Adds a valued parameter, prefixing the key with -- */
		public void add(String key, String value) { args.add(new Arg("--" + key, value)); }
		/** Adds a non-valued parameter, prefixing it with -- */
		public void add(String arg) { args.add(new Arg("--" + arg, null)); }

		public String[] toArray() {
			return args.stream().<String>mapMulti((e, c) -> {
				c.accept(e.key());
				if (e.value() != null)
					c.accept(e.value());
			}).toArray(String[]::new);
		}
	}
}
