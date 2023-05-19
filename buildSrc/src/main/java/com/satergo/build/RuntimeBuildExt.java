package com.satergo.build;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.util.internal.ConfigureUtil;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public class RuntimeBuildExt {

	public static class LauncherScript {
		public enum Type { BAT, SH }

		public Type type;
		public String name;
		public String mainClass;
		public List<String> defaultJvmOpts;

		public boolean windowsConsole = true;
	}


	public String platformName;
	public URI jdkRuntimeURI;
	public String jdkRuntimeRoot = "";
	/**
	 * Whether to cache the generated runtime for next use.
	 * Cache is per the filename from {@link #jdkRuntimeURI} and is never automatically deleted.
	 */
	public boolean cacheRuntimes = true;
	/**
	 * Deletes the {@code legal} directory and the {@code release} file in the output runtime
	 */
	public boolean cleanupRuntimeContent = false;
	/**
	 * Whether to include the program jar in the lib directory of the runtime
	 */
	public boolean includeJarInRuntime = false;

	public List<String> extraModules;
	public List<String> extraJlinkOptions;

	public boolean runProguard;
	public Path proguardConfig;
	public String proguardOutputName;

	public LauncherScript launcherScript;

	public void launcherScript(Closure<?> closure) {
		launcherScript(ConfigureUtil.configureUsing(closure));
	}

	public void launcherScript(Action<? super LauncherScript> action) {
		if (launcherScript == null) launcherScript = new LauncherScript();
		action.execute(launcherScript);
	}

	public Closure<?> doBeforeArchival;

	public void doBeforeArchival(Closure<?> closure) {
		this.doBeforeArchival = closure;
	}

	// For accessing from the doBeforeArchival closure
	public final String runtimeDirectoryName = "runtime";

	/**
	 * Whether to create a zip archive of the final runtime
	 */
	public boolean createArchive = false;
	public Path archiveOutputPath;

}
