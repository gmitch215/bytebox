plugins {
	kotlin("multiplatform") version "2.4.20"
}

kotlin {
	compilerOptions {
		if (providers.gradleProperty("newEh").isPresent) {
			freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
		}
	}

	@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
	wasmJs {
		nodejs()
		binaries.executable()
	}
}
