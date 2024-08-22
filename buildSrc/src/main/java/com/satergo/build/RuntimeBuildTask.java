package com.satergo.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class RuntimeBuildTask extends DefaultTask {

	static final String JDK_CACHE_DIR_NAME = "jdks";

	@TaskAction
	public void execute() throws TaskExecutionException, IOException, InterruptedException {
		RuntimeBuildExt extension = getProject().getExtensions().getByType(RuntimeBuildExt.class);

		// This task supports working with both full and shrunk jars
		boolean dependsOnShrinkJar = getLifecycleDependencies().getDependencies(this)
				.contains(getProject().getTasks().getByName("shrinkJarTask"));

		File mainJar = dependsOnShrinkJar
				? ((File) getProject().getTasks().getByPath("shrinkJarTask").getExtensions().getByName("output"))
				: ShrinkJarTask.getShadowArchiveFile(getProject());
		getExtensions().add("mainJar", mainJar);

		// Invoke jdeps to find used modules
		StringWriter jdepsOut = new StringWriter();
		int jdepsResult = ToolProvider.findFirst("jdeps").orElseThrow().run(new PrintWriter(jdepsOut), new PrintWriter(System.err),
				"--print-module-deps", "--ignore-missing-deps", mainJar.getAbsolutePath());
		if (jdepsResult != 0) throw new TaskExecutionException(this, new RuntimeException());
		LinkedHashSet<String> modules = new LinkedHashSet<>(Arrays.asList(jdepsOut.toString().strip().split(",")));
		// Add extra (likely dynamically used) modules from configuration
		if (extension.extraModules != null)
			modules.addAll(extension.extraModules);
		if (extension.excludeModules != null) {
			extension.excludeModules.forEach(excluded -> {
				if (!modules.remove(excluded)) {
					getLogger().warn("Attempted to exclude module \"{}\" which was not going to be included", excluded);
				}
			});
		}

		// Use from cache or download JDK for runtime
		String[] jdkArchiveLinkParts = extension.jdkRuntimeURI.getPath().split("/");
		String jdkArchiveName = jdkArchiveLinkParts[jdkArchiveLinkParts.length - 1];
		Path cacheDir = getProject().getLayout().getBuildDirectory().get().dir(JDK_CACHE_DIR_NAME).getAsFile().toPath();
		if (!Files.exists(cacheDir))
			Files.createDirectory(cacheDir);
		Path jdk = cacheDir.resolve(jdkArchiveName);
		if (!Files.exists(jdk)) {
			FileUtils.downloadJdk(jdkArchiveName, extension.jdkRuntimeURI, extension.jdkRuntimeRoot, jdk);
		}

		// Invoke jlink to create a runtime
		Path runtimeOutput = getProject().getLayout().getBuildDirectory().get().getAsFile().toPath().resolve("runtime");
		if (Files.exists(runtimeOutput))
			FileUtils.deleteDirectory(runtimeOutput);
		ArrayList<String> args = new ArrayList<>();

		args.add("--module-path");
		List<String> modulePaths = new ArrayList<>();
		modulePaths.add(jdk.resolve("jmods").toAbsolutePath().toString());
		for (File extraModuleJar : extension.extraModuleJars) {
			modulePaths.add(extraModuleJar.getParentFile().getAbsolutePath());
		}
		args.add(String.join(":", modulePaths));

		args.add("--add-modules");
		args.add(String.join(",", modules));

		if (extension.extraJlinkOptions != null)
			args.addAll(extension.extraJlinkOptions);

		args.add("--output");
		args.add(runtimeOutput.toAbsolutePath().toString());

		int jlinkResult = ToolProvider.findFirst("jlink").orElseThrow().run(System.out, System.err, args.toArray(new String[0]));
		if (jlinkResult != 0) throw new TaskExecutionException(this, new RuntimeException());

		// Cleanup the runtime
		if (extension.cleanupRuntimeContent) {
			Files.delete(runtimeOutput.resolve("release"));
			FileUtils.deleteDirectory(runtimeOutput.resolve("legal"));
		}

		// Copy jar
		if (extension.includeJarInRuntime) {
			Files.copy(mainJar.toPath(), runtimeOutput.resolve("lib").resolve(mainJar.getName()));
		}

		// Create launcher script
		if (extension.launcherScript != null) {
			String fileName;
			String templatePath = switch (extension.launcherScript.type) {
				case SH -> "/unixLauncher.sh";
				case BAT -> "/windowsLauncher.bat";
			};
			String optsSerialized = extension.launcherScript.defaultJvmOpts == null ? "" : extension.launcherScript.type == RuntimeBuildExt.LauncherScript.Type.BAT
					? extension.launcherScript.defaultJvmOpts.stream()
						.map(opt -> "\"" + opt
								.replace("{APP_HOME}", "%APP_HOME%")
								.replace("\"", "\"\"") + "\"").collect(Collectors.joining(" "))
					: extension.launcherScript.defaultJvmOpts.stream()
						.map(opt -> "\"" + opt
								.replace("{APP_HOME}", "$APP_HOME") + "\"").collect(Collectors.joining(" "));
			String launcherScript = new String(new DataInputStream(Objects.requireNonNull(getClass().getResourceAsStream(templatePath))).readAllBytes(), StandardCharsets.UTF_8)
					.replace("{MAIN_CLASS}", extension.launcherScript.mainClass)
					.replace("{DEFAULT_JVM_OPTS}", optsSerialized)
					.replace("{WIN_JAVA_BINARY_NAME}", extension.launcherScript.windowsConsole ? "java" : "javaw");
			String launcherName = extension.launcherScript.name + (extension.launcherScript.type == RuntimeBuildExt.LauncherScript.Type.BAT ? ".bat" : "");
			Path launcherPath = runtimeOutput.resolve("bin").resolve(launcherName);
			Files.writeString(launcherPath, launcherScript);
			if (extension.launcherScript.type == RuntimeBuildExt.LauncherScript.Type.SH) {
				launcherPath.toFile().setExecutable(true);
			}
		}

		// Archive runtime
		if (extension.createArchive) {
			if (extension.doBeforeArchival != null) {
				extension.doBeforeArchival.run();
			}
			Files.createDirectories(extension.archiveOutputPath.getParent());
			FileUtils.zipContent(runtimeOutput, extension.archiveOutputPath);
		}
	}

}