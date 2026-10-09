plugins {
	id("dev.gmitch215.bytebox")
	kotlin("jvm") version "2.4.20"
}

bytebox {
	handlerClass = providers.gradleProperty("handler").orElse("com.example.ClassWorker")
	wrangler { name = "kotlin-jvm"; compatibilityDate = "2026-08-22" }
}

if (!providers.gradleProperty("pluginWired").isPresent) {
	tasks.named("compileKotlin") { dependsOn("generateNpmBindings") }
	tasks.named("compileByteboxKotlin") { dependsOn("generateEntryPoint", "generateCodecs", "generateSerialCodecs") }
}
