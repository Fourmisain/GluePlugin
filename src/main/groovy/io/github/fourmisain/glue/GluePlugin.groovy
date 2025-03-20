package io.github.fourmisain.glue

import org.gradle.api.Plugin
import org.gradle.api.Project

class GluePlugin implements Plugin<Project> {
	void apply(Project project) {
		project.plugins.apply 'base'

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
			inputs.property('overrides', extension.overrides)

			if (extension.transforms) {
				doNotTrackState('no reliable way of tracking arbitrary functions/lambdas/closures')
			}

			doLast {
				Glue glue = Glue.of(project)

				def inputs = it.inputs.files.files
				def output = it.outputs.files.singleFile

				def modData = glue.createModData()
				inputs.each {
					println "glueing ${project.file('.').relativePath(it)}"
					modData.merge(glue.readModData(it))
				}
				modData.override(extension.overrides)
				modData.transform(extension.transforms)

				println "writing ${project.file('.').relativePath(output)}"
				Glue.writeModJar(output, modData.jar)
			}
		}

		// add to clean up task
		project.tasks.named('clean') {
			doFirst {
				delete outputFile()
			}
		}

		// automatically glue after building
		project.tasks.named('build') {
			finalizedBy 'glue'
		}
	}
}
