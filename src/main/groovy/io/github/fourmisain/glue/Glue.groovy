package io.github.fourmisain.glue

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import org.gradle.api.Project

import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Glue {
	class ModData {
		Map<String, byte[]> jar = [:]
		Map<String, Object> modJson = [:]
		Map<String, Object> refmap = [:]
		Map<String, Map<String, Object>> mixinConfigs = [:]

		protected ModData() {}
		protected ModData(String subproject) {
			if (subproject) {
				this.jar = readJarMap(inputFile(subproject))
				this.modJson = Glue.fromJson(jar.get("fabric.mod.json"))
				this.refmap  = Glue.fromJson(jar.get(refmapName()))

				for (String mixinName : this.modJson.get('mixins') ?: []) {
					this.mixinConfigs.put(mixinName, Glue.fromJson(jar.get(mixinName)))
				}
			}
		}

		void merge(ModData other) {
			mergeInto(this.jar, other.jar, true)
			mergeInto(this.modJson, other.modJson, true)
			mergeInto(this.refmap, other.refmap, true)

			// update jar contents
			this.jar.put("fabric.mod.json", Glue.toJson(this.modJson))
			this.jar.put(refmapName(), Glue.toJson(this.refmap))

			other.mixinConfigs.each { otherMixinName, otherMixinConfig -> {
				// add or merge other mixin config
				def newMixinConfig = this.mixinConfigs.merge(otherMixinName, otherMixinConfig, (currentMixin, otherMixin) -> {
					mergeInto(currentMixin, otherMixin, true)
					return currentMixin
				})

				// update jar contents
				this.jar.put(otherMixinName, Glue.toJson(newMixinConfig))
			}}
		}
	}

	static GSON = new GsonBuilder()
		.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	private static Map<Project, Glue> INSTANCES = [:]
	private Project project
	private GluePluginExtension extension

	private Glue(Project project, GluePluginExtension extension) {
		this.project = project
		this.extension = extension
		INSTANCES.put(project, this)
	}

	static void init(Project project, GluePluginExtension extension) {
		INSTANCES.put(project, new Glue(project, extension))
	}

	static Glue of(Project project) {
		return INSTANCES.get(project)
	}

	ModData createModData() {
		return new ModData()
	}

	ModData readModData(String subproject) {
		return new ModData(subproject);
	}

	void writeModJar(Map<String, byte[]> jar) {
		outputPath().mkdirs()

		try (def out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile())))) {
			for (entry in jar.entrySet()) {
				def path = entry.key
				def bytes = entry.value

				out.putNextEntry(new ZipEntry(path))
				out.write(bytes)
				out.closeEntry();
			}
		}
	}

	Map<String, byte[]> readJarMap(File file) {
		Map<String, byte[]> jarMap = [:]

		project.zipTree(file).visit {
			if (it.isDirectory()) return

			try (def input = it.open()) {
				jarMap.put(it.getPath(), input.readAllBytes())
			}
		}

		return jarMap
	}

	// TODO shouldn't be hardcoded
	File inputFile(String subprojectName) {
		return project.file("${subprojectName}/build/libs/${project.archives_base_name}-${project.mod_version}.jar")
	}

	String refmapName() {
		return project.archives_base_name + '-refmap.json'
	}

	File outputPath() {
		return project.file('build/libs')
	}

	File outputFile() {
		return new File(outputPath(), (extension.outputName ?: "${project.archives_base_name}-${project.mod_version}") + ".jar")
	}

	private boolean valuesChanged(Object key, Object v1, Object v2) {
		// ignore these as we'll merge them afterwards
		if (key == "fabric.mod.json" || key == refmapName() || key.endsWith('.mixins.json'))
			return false;

		// ignore since it should never matter
		if (key == "META-INF/MANIFEST.MF")
			return false;

		if (v1 instanceof byte[] && v2 instanceof byte[])
			return !Arrays.equals(v1, v2)

		return v1 != v2;
	}

	void mergeInto(Map target, Map source, boolean logging = false) {
		source.forEach((k, v) -> {
			def exist = target.get(k)

			if (exist == null) {
				target.put(k, v)
			} else if (v instanceof Map) {
				// merge maps
				if (!exist instanceof Map) throw new Error("mismatched types, new: ${v}, old: ${exist}")

				target.putIfAbsent(k, [:])
				mergeInto(exist, v, logging)
			} else if (v instanceof List) {
				// merge lists (without duplicates)
				if (!exist instanceof List) throw new Error("mismatched types, new: ${v}, old: ${exist}")

				target.put(k, (exist as Set + v as Set) as List)
			} else {
				// overwrite value
				if (logging && valuesChanged(k, v, exist)) {
					print "overwriting value for ${k}"
					if (v instanceof String && exist instanceof String)
						print ", from ${exist} to ${v}"
					println()
				}

				target.put(k, v)
			}
		})
	}

	static Map<String, Object> fromJson(byte[] data) {
		if (data == null) return [:]
		def str = new String(data, StandardCharsets.UTF_8)
		return GSON.fromJson(str, Map<String, Object>.class)
	}

	static byte[] toJson(Map<String, Object> json) {
		return GSON.toJson(json).getBytes(StandardCharsets.UTF_8)
	}
}
