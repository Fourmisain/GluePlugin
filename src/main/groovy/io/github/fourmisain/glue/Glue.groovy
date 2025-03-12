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
		String jarName
		Map<String, Map<String, Object>> modJsons = [:]
		Map<String, Map<String, Object>> mixinConfigs = [:]
		Map<String, Map<String, Object>> refmaps = [:]

		protected ModData() {}
		protected ModData(File jarFile) {
			if (jarFile) {
				this.jar = readJarMap(jarFile)
				this.jarName = jarFile.name

				this.modJsons = [
					'fabric.mod.json': Glue.fromJson(jar.get("fabric.mod.json"))
				]

				this.modJsons.values().each { modJson ->
					// read mixin config from mod json
					modJson.get('mixins').each { mixinName ->
						def mixinConfig = Glue.fromJson(jar.get(mixinName))
						this.mixinConfigs.put(mixinName, mixinConfig)

						// read refmap from mixin config
						def refmapName = mixinConfig.get('refmap')
						if (refmapName) {
							this.refmaps.putIfAbsent(refmapName, Glue.fromJson(jar.get(refmapName)))
						}
					}
				}
			}
		}

		void merge(ModData other) {
			mergeInto(this.jar, other.jar, this.jarName)
			mergeEachInto(this.modJsons, other.modJsons)
			mergeEachInto(this.mixinConfigs, other.mixinConfigs)
			mergeEachInto(this.refmaps, other.refmaps)
		}

		private void mergeEachInto(Map<String, Map<String, Object>> thisMap, Map<String, Map<String, Object>> otherMap) {
			otherMap.each { otherKey, otherValue ->  {
				// add or merge other
				def newValue = thisMap.merge(otherKey, otherValue) { leftValue, rightValue ->
					mergeInto(leftValue, rightValue, otherKey)
					return leftValue
				}

				// update jar contents
				this.jar.put(otherKey, Glue.toJson(newValue))
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

	private Glue(Project project) {
		this.project = project
	}

	static Glue of(Project project) {
		return INSTANCES.computeIfAbsent(project, p -> new Glue(project))
	}

	ModData createModData() {
		return new ModData()
	}

	ModData readModData(File jarFile) {
		return new ModData(jarFile);
	}

	static void writeModJar(File jarPath, Map<String, byte[]> jar) {
		jarPath.parentFile.mkdirs()

		try (def out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jarPath)))) {
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

	// only used for logging purposes
	static private boolean valuesChanged(Object key, Object v1, Object v2) {
		// ignore these as we'll merge them afterwards
		if (key.endsWith(".mod.json") || key.endsWith('-refmap.json') || key.endsWith('.mixins.json'))
			return false;

		// ignore since it shouldn't matter
		if (key == "META-INF/MANIFEST.MF")
			return false;

		if (v1 instanceof byte[] && v2 instanceof byte[])
			return !Arrays.equals(v1, v2)

		return v1 != v2;
	}

	static void mergeInto(Map target, Map source, String loggedFile = null) {
		source.forEach((k, v) -> {
			def exist = target.get(k)

			if (exist == null) {
				target.put(k, v)
			} else if (v instanceof Map) {
				// merge maps
				if (!exist instanceof Map) throw new Error("mismatched types, new: ${v}, old: ${exist}")

				target.putIfAbsent(k, [:])
				mergeInto(exist, v, loggedFile)
			} else if (v instanceof List) {
				// merge lists (without duplicates)
				if (!exist instanceof List) throw new Error("mismatched types, new: ${v}, old: ${exist}")

				target.put(k, (exist as Set + v as Set) as List)
			} else {
				// overwrite value
				if (loggedFile && valuesChanged(k, v, exist)) {
					print "${loggedFile}: \"${k}\": "
					if (v instanceof String && exist instanceof String)
						print "${exist} -> ${v}"
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
