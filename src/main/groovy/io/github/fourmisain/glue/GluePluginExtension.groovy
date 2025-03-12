package io.github.fourmisain.glue

class GluePluginExtension {
	List<String> targets
	Map<String, String> inputNames = [:]  // target -> input name
	String outputName
}
