## Glue

Gradle plugin that helps build multi-version Fabric Minecraft mods by glueing together multiple builds.

"Glueing" (sic) means combining the jars, overwriting existing files - except for the `fabric.mod.json`, mixin configurations and refmap JSON files.  
These 3 types of files are instead combined as a sort of map/object, where existing entries are overwritten.

### Setup

`build.gradle`:

```groovy
plugins {
    id 'glue' version '0.0.5'
}
```

`settings.gradle`:
```groovy
pluginManagement {
    repositories {
        maven {
            name = "Fourmisain's Maven"
            url = "https://gitlab.com/api/v4/projects/37712942/packages/maven"
        }
    }
}
```

### Configuring

`build.gradle` example:
```groovy
glue {
    // subproject names in the order they'll be glued
    targets = ['legacy', 'main', 'glue']

    // output path is 'build/libs', default output file name is "${project.archives_base_name}-${project.mod_version}"
    outputName = "${project.archives_base_name}-${project.mod_version}-universal"

    // input path is "${target}/build/libs", default input file name is "${subproject.archives_base_name}-${subproject.mod_version}"
    inputNames = [
        legacy: 'testName'  // overrides the default name for the 'legacy' target
    ]
}
```

### Building

Build with `gradle build` or `gradle glue`

### Example

The easiest way to understand how to use this plugin is to look at an example usage: [Creative One-Punch](https://github.com/Fourmisain/CreativeOnePunch)

This mod has the `legacy` and `main` subprojects, which build the mod for old and modern Minecraft versions.  
Since the mod only uses one simple Mixin, the idea is to add a Mixin plugin which applies the correct Mixin from either build based on the Minecraft version that's running - this is what the `glue` subproject is supposed to do.

The java files all have unique names/paths to ensure they won't be overwritten. The Mixins need to live in the same base package (but can be in sub packages) - in this case one of the Mixins simply has `Legacy` in its name,  which is also used to determine if it should run on old Minecraft versions or not.

Inside `build.gradle`, Glue is configured to glue `legacy`, `main`, `glue` in order, hence `glue` gets to decide e.g. the Minecraft version range in `fabric.mod.json` and especially the `plugin` value in `creativeonepunch.mixins.json`.