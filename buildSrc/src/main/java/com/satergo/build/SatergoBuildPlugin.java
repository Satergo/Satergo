package com.satergo.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SatergoBuildPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getTasks().register("shrinkJarTask", ShrinkJarTask.class).configure(task -> {
			task.setGroup("other");
			task.dependsOn("shadowJar");
		});

		project.getTasks().register("satergoRuntime", RuntimeBuildTask.class).configure(task -> {
			task.setGroup("build");
			if (!project.hasProperty("doNotShrink"))
				task.dependsOn("shrinkJarTask");
			else task.dependsOn("shadowJar");
		});
		project.getExtensions().create("satergoRuntime", RuntimeBuildExt.class);

		project.getTasks().register("satergoWinInstaller", WinInstallerBuildTask.class).configure(task -> {
			task.setGroup("build");
			task.dependsOn("satergoRuntime");
		});
		project.getExtensions().create("satergoWinInstaller", WinInstallerBuildExt.class);
	}
}
