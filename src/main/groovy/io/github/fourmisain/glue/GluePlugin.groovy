package io.github.fourmisain.glue

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete

class GluePlugin implements Plugin<Project> {
	void apply(Project project) {
		GluePluginExtension extension

		def glueTask = project.task('glue') {
			group 'build'
			description 'Glues together all projects into one fat mod jar.'
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

		extension = project.extensions.create('glue', GluePluginExtension, glueTask)
		Glue.init(project, extension)

		// get/create clean task
		var cleanTask = project.tasks.findByPath('clean')
		if (!cleanTask) {
			cleanTask = project.tasks.create('clean', Delete) {
				group 'build'
			}
		}

		// define cleanup
		cleanTask.doFirst {
			delete Glue.of(project).outputFile()
		}

		// get/create build task
		var buildTask = project.tasks.findByPath('build')
		if (!buildTask) {
			buildTask = project.tasks.create('build') {
				group 'build'
			}
		}

		// glue after building
		buildTask.finalizedBy('glue')
	}
}