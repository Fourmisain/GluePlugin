package io.github.fourmisain.glue

import org.gradle.api.Plugin
import org.gradle.api.Project

class GluePlugin implements Plugin<Project> {
	void apply(Project project) {
		GluePluginExtension extension

		def glueTask = project.task('glue') {
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
	}
}