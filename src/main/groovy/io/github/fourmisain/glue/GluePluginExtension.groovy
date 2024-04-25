package io.github.fourmisain.glue

import org.gradle.api.Task

class GluePluginExtension {
	private Task glueTask
	List<String> targets
	String outputName

	GluePluginExtension(Task glueTask) {
		this.glueTask = glueTask
	}

	void setTargets(List<String> targets) {
		this.targets = targets.asImmutable()

		glueTask.dependsOn.clear()
		glueTask.dependsOn targets.collect {
			":${it}:build"
		}
	}
}
