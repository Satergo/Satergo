package com.satergo.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;
import proguard.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class RuntimeBuildTask extends DefaultTask {

	private static final String JDK_CACHE_DIR_NAME = "jdks";

	// Avoid depending on the plugin because it causes problems
	@SuppressWarnings("unchecked")
	private File getShadowArchiveFile() {
		try {
			Task shadowJarTask = getProject().getTasks().getByName("shadowJar");
			Provider<RegularFile> regularFileProvider = (Provider<RegularFile>)
					shadowJarTask.getClass().getMethod("getArchiveFile").invoke(shadowJarTask);
			return regularFileProvider.get().getAsFile();
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	@TaskAction
	public void execute() throws TaskExecutionException, IOException, ParseException, InterruptedException {
		RuntimeBuildExt extension = getProject().getExtensions().getByType(RuntimeBuildExt.class);
		File shadowJarFile = getShadowArchiveFile();

		// Run proguard
		File proguardOutputFile = new File(getProject().getBuildDir(), "libs/" + extension.proguardOutputName);
		if (extension.runProguard) {
			Configuration config = new Configuration();
			try (ConfigurationParser configurationParser = new ConfigurationParser(getProject().file(extension.proguardConfig), System.getProperties())) {
				configurationParser.parse(config);
			}

			config.libraryJars = new ClassPath();
			for (File jmod : Objects.requireNonNull(new File(System.getProperty("java.home") + "/jmods").listFiles())) {
				ClassPathEntry classPathEntry = new ClassPathEntry(jmod, false);
				classPathEntry.setJarFilter(Collections.singletonList("!**.jar"));
				classPathEntry.setFilter(Collections.singletonList("!module-info.class"));
				config.libraryJars.add(classPathEntry);
			}

			config.programJars = new ClassPath();
			config.programJars.add(new ClassPathEntry(shadowJarFile, false));
			config.programJars.add(new ClassPathEntry(proguardOutputFile, true));

			try {
				new ProGuard(config).execute();
			} catch (Exception e) {
				throw new RuntimeException("ProGuard exception", e);
			}
		}

		File mainJar = extension.runProguard ? proguardOutputFile : shadowJarFile;

		// Invoke jdeps to find used modules
		StringWriter jdepsOut = new StringWriter();
		int jdepsResult = ToolProvider.findFirst("jdeps").orElseThrow().run(new PrintWriter(jdepsOut), new PrintWriter(System.err),
				"--print-module-deps", "--ignore-missing-deps", mainJar.getAbsolutePath());
		if (jdepsResult != 0) throw new TaskExecutionException(this, new RuntimeException());
		LinkedHashSet<String> modules = new LinkedHashSet<>(Arrays.asList(jdepsOut.toString().strip().split(",")));
		// Add extra (likely dynamically used) modules from configuration
		if (extension.extraModules != null)
			modules.addAll(extension.extraModules);

		// Use from cache or download JDK for runtime
		String[] jdkArchiveLinkParts = extension.jdkRuntimeURI.getPath().split("/");
		String jdkArchiveName = jdkArchiveLinkParts[jdkArchiveLinkParts.length - 1];
		Path cacheDir = getProject().getBuildDir().toPath().resolve(JDK_CACHE_DIR_NAME);
		if (!Files.exists(cacheDir))
			Files.createDirectory(cacheDir);
		Path jdk = cacheDir.resolve(jdkArchiveName);
		if (!Files.exists(jdk)) {
			HttpResponse<InputStream> request = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
					.send(HttpRequest.newBuilder().uri(extension.jdkRuntimeURI).build(), HttpResponse.BodyHandlers.ofInputStream());
			FileUtils.ArchiveType archiveType;
			if (jdkArchiveName.endsWith(".zip")) archiveType = FileUtils.ArchiveType.ZIP;
			else if (jdkArchiveName.endsWith(".tar.gz")) archiveType = FileUtils.ArchiveType.TAR_GZ;
			else throw new IllegalArgumentException("unsupported archive type");
			Files.createDirectory(jdk);
			Function<String, String> pathRewriter = name -> {
				// appears in linux & mac archives
				if (name.startsWith("./")) name = name.substring(2);
				// skip top directory
				name = name.substring(name.indexOf('/') + 1);
				// skip path to root
				if (name.startsWith(extension.jdkRuntimeRoot))
					return name.substring(extension.jdkRuntimeRoot.length());
				else return null;
			};
			switch (archiveType) {
				case ZIP -> FileUtils.extractZipTo(request.body(), jdk, pathRewriter);
				case TAR_GZ -> FileUtils.extractTarGzTo(request.body(), jdk, pathRewriter);
			}
		}

		// Invoke jlink to create a runtime
		Path runtimeOutput = getProject().getBuildDir().toPath().resolve("runtime");
		ArrayList<String> args = new ArrayList<>();

		args.add("--module-path");
		args.add(jdk.resolve("jmods").toAbsolutePath().toString());

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

		if (extension.doBeforeArchival != null) {
			extension.doBeforeArchival.run();
		}

		// Archive runtime
		if (extension.createArchive) {
			Files.createDirectories(extension.archiveOutputPath.getParent());
			FileUtils.zipContent(runtimeOutput, extension.archiveOutputPath);
		}
	}

}