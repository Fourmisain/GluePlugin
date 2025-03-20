package io.github.fourmisain.glue

import org.gradle.api.Action

@SuppressWarnings('unused')
class GluePluginExtension {
	List<String> targets
	Map<String, String> inputNames = [:]  // target -> input name
	String outputName
	Map<String, Map<String, Object>> overrides = [:]  // file -> entry -> Object
	private Map<String, Action<Map<String, Object>>> transforms = [:] // file -> Action

	void transform(String jsonFilename, Action<Map<String, Object>> action) {
		transforms.put(jsonFilename, action)
	}

	Map<String, Action<Map<String, Object>>> getTransforms() {
		return transforms
	}
}
