pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
	includeBuild("..")
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

includeBuild("..")

rootProject.name = "bytebox-experiments"

include("wrappers", "lib-json", "lib-gson", "lib-slf4j", "lib-codec", "lib-collections4", "lib-joda", "lib-snakeyaml", "lib-okhttp", "memory", "monitors", "probe", "conformance", "isolate", "jsbody", "dep-none", "dep-guava", "dep-commons", "dep-jackson", "dep-log4j", "dep-jackson-core", "dep-log4j-api")
include("kotlin-jvm", "kotlin-coroutines")
include("lock-delegation")
include("kotlin-wasmjs", "kotlin-wasmwasi")
include("kotlin-js")
include("socket-write")
