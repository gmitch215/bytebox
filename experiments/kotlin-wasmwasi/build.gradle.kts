plugins {
	kotlin("multiplatform") version "2.4.20"
}

kotlin {
	compilerOptions {
		if (providers.gradleProperty("legacyEh").isPresent) {
			freeCompilerArgs.add("-Xwasm-use-new-exception-proposal=false")
		}
	}

	@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
	wasmWasi {
		nodejs()
		binaries.executable()
	}
}
