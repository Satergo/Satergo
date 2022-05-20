package com.satergo.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SatergoBuildPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getTasks().register("satergoRuntime", RuntimeBuildTask.class).configure(task -> {
			task.setGroup("build");
			task.dependsOn("shadowJar");
		});
		project.getExtensions().create("satergoRuntime", RuntimeBuildExt.class);
	}
}
