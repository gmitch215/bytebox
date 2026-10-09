plugins {
	kotlin("multiplatform") version "2.4.20"
}

kotlin {
	js {
		useEsModules()
		nodejs()
		binaries.executable()
	}

	sourceSets {
		jsMain.dependencies {
			implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
		}
	}
}
