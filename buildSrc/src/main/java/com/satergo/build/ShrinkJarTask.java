package com.satergo.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import proguard.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class ShrinkJarTask extends DefaultTask {

	// Avoid depending on the plugin because it causes problems
	@SuppressWarnings("unchecked")
	public static File getShadowArchiveFile(Project project) {
		try {
			Task shadowJarTask = project.getTasks().getByName("shadowJar");
			Provider<RegularFile> regularFileProvider = (Provider<RegularFile>)
					shadowJarTask.getClass().getMethod("getArchiveFile").invoke(shadowJarTask);
			return regularFileProvider.get().getAsFile();
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	@TaskAction
	public void execute() throws TaskExecutionException, ParseException, IOException {
		RuntimeBuildExt ext = getProject().getExtensions().getByType(RuntimeBuildExt.class);
		File shadowJarFile = getShadowArchiveFile(getProject());

		boolean preserveTimestamps = ((AbstractArchiveTask) getProject().getTasks().getByName("shadowJar")).isPreserveFileTimestamps();
		boolean reproducibleFileOrder = ((AbstractArchiveTask) getProject().getTasks().getByName("shadowJar")).isReproducibleFileOrder();
		boolean recompress = !preserveTimestamps || reproducibleFileOrder;

		// Run proguard
		File finalOutputFile = new File(getProject().getLayout().getBuildDirectory().get().getAsFile(), "libs/" + ext.proguardOutputName);
		File proguardOutputFile = !recompress ? finalOutputFile : new File(getProject().getLayout().getBuildDirectory().get().getAsFile(), "tmp/shrinkJarTask/" + ext.proguardOutputName);
		if (!proguardOutputFile.getParentFile().exists()) {
			if (!proguardOutputFile.getParentFile().mkdirs())
				throw new IOException();
		}
		Configuration config = new Configuration();
		try (ConfigurationParser configurationParser = new ConfigurationParser(ext.proguardConfig.toFile(), System.getProperties())) {
			configurationParser.parse(config);
		}

		config.libraryJars = new ClassPath();
		for (File jmod : Objects.requireNonNull(new File(System.getProperty("java.home") + "/jmods").listFiles())) {
			ClassPathEntry classPathEntry = new ClassPathEntry(jmod, false);
			classPathEntry.setJarFilter(Collections.singletonList("!**.jar"));
			classPathEntry.setFilter(Collections.singletonList("!module-info.class"));
			config.libraryJars.add(classPathEntry);
		}
		for (File extraModuleJar : ext.extraModuleJars) {
			ClassPathEntry classPathEntry = new ClassPathEntry(extraModuleJar, false);
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

		if (recompress) {
			// Create a new archive from the temporary archive's contents
			try (var fs = FileSystems.newFileSystem(proguardOutputFile.toPath(), Map.of("enablePosixFileAttributes", true))) {
				FileUtils.zipContent(fs.getPath("/"), finalOutputFile.toPath(), preserveTimestamps, reproducibleFileOrder);
			}
			Files.delete(proguardOutputFile.toPath());
		}
		getExtensions().add("output", finalOutputFile);
	}
}
