package io.github.fourmisain.glue

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.annotations.Nullable

import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@SuppressWarnings('UnnecessaryQualifiedReference')
class Glue {
	class ModData {
		Map<String, byte[]> jar = [:]
		Map<String, Map<String, Object>> modJsons = [:]
		Map<String, Map<String, Object>> mixinConfigs = [:]
		Map<String, Map<String, Object>> refmaps = [:]

		protected ModData() {}
		protected ModData(File jarFile) {
			if (jarFile) {
				this.jar = readJarMap(jarFile)

				def fabricModJson = jar.get("fabric.mod.json")
				if (fabricModJson != null) {
					this.modJsons.put("fabric.mod.json", Glue.fromJson(fabricModJson))
				}

				this.modJsons.each { modJsonName, modJson ->
					// read mixin config from mod json
					modJson.get('mixins').each { mixinName ->
						def mixinConfig = jar.get(mixinName)
						if (mixinConfig == null)
							throw new AssertionError("mod json ${modJsonName} declares mixin config ${mixinName} which does not exist")
						mixinConfig = Glue.fromJson(mixinConfig)
						this.mixinConfigs.put(mixinName, mixinConfig)

						// read refmap from mixin config
						def refmapName = mixinConfig.get('refmap')
						if (refmapName != null) {
							def refmap = jar.get(refmapName)
							if (refmap == null)
								throw new AssertionError("mixin config ${mixinName} declares refmap ${refmapName} which does not exist")
							refmap = Glue.fromJson(refmap)
							this.refmaps.putIfAbsent(refmapName, refmap)
						}
					}
				}
			}
		}

		void merge(ModData other) {
			mergeInto(this.jar, other.jar, new LogData(this.modJsons.keySet() + this.mixinConfigs.keySet() + this.refmaps.keySet() + "META-INF/MANIFEST.MF"))
			mergeEachInto(this.modJsons, other.modJsons)
			mergeEachInto(this.mixinConfigs, other.mixinConfigs)
			mergeEachInto(this.refmaps, other.refmaps)
		}

		private void mergeEachInto(Map<String, Map<String, Object>> thisMap, Map<String, Map<String, Object>> otherMap) {
			otherMap.each { otherKey, otherValue -> {
				// add or merge other
				def newValue = thisMap.merge(otherKey, otherValue) { leftValue, rightValue ->
					mergeInto(leftValue, rightValue, new LogData(otherKey))
					return leftValue
				}

				// update jar contents
				this.jar.put(otherKey, Glue.toJson(newValue))
			}}
		}

		void override(Map<String, Map<String, Object>> overrides) {
			overrideEach(this.modJsons, overrides)
			overrideEach(this.mixinConfigs, overrides)
			overrideEach(this.refmaps, overrides)
		}

		private overrideEach(Map<String, Map<String, Object>> files, Map<String, Map<String, Object>> overrides) {
			overrides.each { file, override ->
				def value = files.get(file)
				if (value != null) {
					println "overriding contents of $file:"

					override.each { key, newEntry ->
						// change or remove entry
						value.compute(key) { k, entry ->

							def prettyEntry
							if (newEntry instanceof String || newEntry instanceof GString) {
								prettyEntry = "\"$newEntry\""
							} else if (newEntry == null) {
								prettyEntry = 'removed'
							} else {
								prettyEntry = newEntry
							}

							println "\"$key\": ${prettyEntry}"

							return newEntry
						}
					}

					// update jar contents
					this.jar.put(file, Glue.toJson(value))
				}
			}
		}

		void transform(Map<String, Action<Map<String, Object>>> transforms) {
			transformEach(this.modJsons, transforms)
			transformEach(this.mixinConfigs, transforms)
			transformEach(this.refmaps, transforms)
		}

		private transformEach(Map<String, Map<String, Object>> files, Map<String, Action<Map<String, Object>>> transforms) {
			transforms.each { file, transform ->

				def value = files.get(file)
				if (value != null) {
					println "transforming file $file"

					transform.execute(value)

					// update jar contents
					this.jar.put(file, Glue.toJson(value))
				}
			}
		}
	}

	static GSON = new GsonBuilder()
		.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create()

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
		return new ModData(jarFile)
	}

	static void writeModJar(File jarPath, Map<String, byte[]> jar) {
		jarPath.parentFile.mkdirs()

		try (def out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jarPath)))) {
			for (entry in jar.entrySet()) {
				def path = entry.key
				def bytes = entry.value

				out.putNextEntry(new ZipEntry(path))
				out.write(bytes)
				out.closeEntry()
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

	static class LogData {
		String jsonFilename
		String path
		Set<String> ignoredPaths

		LogData(Set<String> ignoredPaths) {
			this(null, "", ignoredPaths)
		}

		LogData(@Nullable String jsonFilename, String path = "", Set<String> ignoredPaths = Set.of()) {
			this.jsonFilename = jsonFilename
			this.path = path
			this.ignoredPaths = ignoredPaths
		}

		String path(String key) {
			return path ? "$path/$key" : key
		}

		boolean isIgnored(String key) {
			return path(key) in ignoredPaths
		}

		LogData appendPath(String key) {
			return new LogData(this.jsonFilename, path(key), this.ignoredPaths)
		}
	}

	static void mergeInto(Map target, Map source, LogData logData = null) {
		source.each { k, v ->
			def exist = target.get(k)

			if (exist == null) {
				target.put(k, v)
			} else if (v instanceof Map) {
				// merge maps

				if (!(exist instanceof Map))
					throw new Error("mismatched types, new: ${v}, old: ${exist}")

				mergeInto(exist, v, logData?.appendPath(k as String))
			} else if (v instanceof List<String>) {
				// merge lists (without duplicates)

				if (!exist instanceof List)
					throw new Error("mismatched types, new: ${v}, old: ${exist}")

				target.put(k, (exist as Set + v as Set) as List)
			} else {
				// overwrite value

				if (logData != null && !logData.isIgnored(k as String) && v != exist) {
					def toPrint = { it instanceof String || it instanceof Number }
					def path = (logData.jsonFilename ? logData.jsonFilename + '/' : '') + logData.path(k as String)

					if (toPrint(v) && toPrint(exist)) {
						println "$path:"
						println "$exist -> $v"
					} else {
						println "overwriting $path"
					}
				}

				target.put(k, v)
			}
		}
	}

	static Map<String, Object> fromJson(byte[] data) {
		Objects.requireNonNull(data)
		def str = new String(data, StandardCharsets.UTF_8)
		return GSON.fromJson(str, Map<String, Object>.class)
	}

	static byte[] toJson(Map<String, Object> json) {
		Objects.requireNonNull(json)
		return GSON.toJson(json).getBytes(StandardCharsets.UTF_8)
	}
}
