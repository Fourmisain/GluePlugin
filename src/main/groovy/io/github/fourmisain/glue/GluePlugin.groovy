package io.github.fourmisain.glue

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete

class GluePlugin implements Plugin<Project> {
	void apply(Project project) {
		def extension = project.extensions.create('glue', GluePluginExtension)

		def inputFile = { String target ->
			def subproject = project.project(":$target")
			def name = extension.inputNames.get(target, "${subproject.archives_base_name}-${subproject.mod_version}" as String)
			project.file("${target}/build/libs/${name}.jar")
		}

		def outputFile = {
			def name = extension.outputName ?: "${project.archives_base_name}-${project.mod_version}"
			project.file("build/libs/${name}.jar")
		}

		project.tasks.register('glue') {
			group = 'build'
			description = 'Glues together all projects into one fat mod jar.'

			dependsOn extension.targets.collect {
				":${it}:build"
			}

			onlyIf {
				!project.tasks.findByName('build')?.state?.failure
			}

			// define inputs/outputs
			for (target in extension.targets) {
				inputs.file(inputFile(target))
			}
			outputs.file(outputFile())

			doLast {
				Glue glue = Glue.of(project)

				def inputs = it.inputs.files.files
				def output = it.outputs.files.singleFile

				def modData = glue.createModData()
				inputs.each {
					println "glueing ${project.file('.').relativePath(it)}"
					modData.merge(glue.readModData(it))
				}

				println "writing ${project.file('.').relativePath(output)}"
				Glue.writeModJar(output, modData.jar)
			}
		}

		// add to clean up task / register clean task
		def clean = { delete outputFile() }

		var cleanTask = project.tasks.findByPath('clean')
		if (!cleanTask) {
			project.tasks.register('clean', Delete) {
				group = 'build'

				doFirst clean
			}
		} else {
			cleanTask.doFirst clean
		}

		// automatically glue after building / register build task
		var buildTask = project.tasks.findByPath('build')
		if (!buildTask) {
			project.tasks.register('build') {
				group = 'build'

				finalizedBy 'glue'
			}
		} else {
			buildTask.finalizedBy 'glue'
		}
	}
}
