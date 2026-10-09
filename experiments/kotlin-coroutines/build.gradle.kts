plugins {
	id("dev.gmitch215.bytebox")
	kotlin("jvm") version "2.4.20"
}

val program = providers.gradleProperty("program").orElse("empty").get()

kotlin.sourceSets.named("main") { kotlin.srcDir("src/programs/$program") }

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0")
}

bytebox {
	handlerClass = "com.example.CoWorker"
	wrangler { name = "kotlin-coroutines"; compatibilityDate = "2026-08-22" }
}

tasks.named("compileKotlin") { dependsOn("generateNpmBindings") }
tasks.named("compileByteboxKotlin") { dependsOn("generateEntryPoint", "generateCodecs", "generateSerialCodecs") }

if (providers.gradleProperty("names").isPresent) {
	extensions.configure<org.teavm.gradle.api.TeaVMExtension>("teavm") {
		wasmGC {
			obfuscated.set(false)
			debugInformation.set(true)
		}
	}
}
