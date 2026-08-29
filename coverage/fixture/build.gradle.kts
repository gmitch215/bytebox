import org.teavm.gradle.api.OptimizationLevel

plugins {
	alias(libs.plugins.teavm)
}

description = "The workers lane's fixture, compiled from an instrumented core"

val instrument = project(":bytebox-coverage").tasks.named("instrumentCore")

sourceSets["main"].java.setSrcDirs(listOf(rootDir.resolve("npm/tests/fixtures")))

dependencies {
	// the instrumented classes stand in for bytebox-core, so the project is deliberately not
	// depending on it: two copies on one classpath would resolve to whichever came first
	implementation(files(rootDir.resolve("coverage/build/instrumented")))
	// core's resources, which the instrumented classes do not carry: the substitution policies are
	// registered through META-INF/services, and without them java.net has no implementation at all
	// and java.util.regex quietly resolves to the compiler's own rather than to bytebox's
	implementation(files(rootDir.resolve("core/build/resources/main")))
	implementation(project(":bytebox-coverage"))
	implementation(libs.teavm.jso)
	implementation(libs.teavm.jso.apis)
	implementation(libs.threetenbp)
}

tasks.named("compileJava") {
	dependsOn(instrument, project(":bytebox-core").tasks.named("processResources"))
}

// both write into the fixtures directory, so they are ordered rather than left to race
tasks.named("generateWasmGC") { dependsOn(":bytebox-fixture:generateWasmGC") }

teavm {
	// not outOfProcess: a compile of its own does not find the substitution policies, and java.net
	// then resolves to the compiler's own XMLHttpRequest-backed classes rather than to bytebox's
	all { mainClass = "fixture.Entry" }
	wasmGC {
		modularRuntime = true
		minDirectBuffersSize = 1
		// readable names, because a probe that never fires is easier to explain with them
		obfuscated = false
		debugInformation = false
		sourceMap = false
		optimization = OptimizationLevel.AGGRESSIVE
		outputDir = rootDir.resolve("npm/tests/fixtures")
		relativePathInOutputDir = ""
		targetFileName = "core-coverage.wasm"
	}
}
