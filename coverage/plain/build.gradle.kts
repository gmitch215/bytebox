import org.teavm.gradle.api.OptimizationLevel

plugins {
	alias(libs.plugins.teavm)
}

description = "The workers lane's fixture, as a project would compile it"

sourceSets["main"].java.setSrcDirs(listOf(rootDir.resolve("npm/tests/fixtures")))

dependencies {
	implementation(project(":bytebox-core"))
	implementation(libs.teavm.jso)
	implementation(libs.teavm.jso.apis)
	implementation(libs.threetenbp)
}

teavm {
	all {
		mainClass = "fixture.Entry"
	}
	wasmGC {
		modularRuntime = true
		minDirectBuffersSize = 1
		obfuscated = true
		debugInformation = false
		sourceMap = false
		optimization = OptimizationLevel.AGGRESSIVE
		outputDir = rootDir.resolve("npm/tests/fixtures")
		relativePathInOutputDir = ""
		targetFileName = "core.wasm"
	}
}
