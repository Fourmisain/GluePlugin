package io.github.fourmisain.glue

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete

class GluePlugin implements Plugin<Project> {
	void apply(Project project) {
		def extension = project.extensions.create('glue', GluePluginExtension)

		project.tasks.register('glue') {
			group = 'build'
			description = 'Glues together all projects into one fat mod jar.'

			dependsOn extension.targets.collect {
				":${it}:build"
			}

			onlyIf {
				!project.tasks.findByName('build')?.state?.failure
			}

			// TODO https://docs.gradle.org/current/userguide/more_about_tasks.html
			doNotTrackState("can't be botherered right now")

			doLast {
				Glue glue = Glue.of(project)

				def mergedData = glue.createModData()
				extension.targets.each {
					mergedData.merge(glue.readModData(it))
				}
				glue.writeModJar(mergedData.jar)
			}
		}

		Glue.init(project, extension)

		def clean = {
			delete Glue.of(project).outputFile()
		}

		// add to clean up task (or register new task)
		var cleanTask = project.tasks.findByPath('clean')
		if (!cleanTask) {
			project.tasks.register('clean', Delete) {
				group = 'build'

				doFirst clean
			}
		} else {
			cleanTask.doFirst clean
		}

		// automatically glue after building
		var buildTask = project.tasks.findByPath('build')
		if (!buildTask) {
			project.tasks.register('build') {
				group = 'build'

				finalizedBy 'glue'
			}
		} else {
			buildTask.finalizedBy('glue')
		}
	}
}
