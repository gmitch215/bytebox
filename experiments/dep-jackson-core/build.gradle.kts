plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
}

bytebox {
	handlerClass = "com.example.JacksonCoreWorker"
	wrangler { name = "dep-jackson-core"; compatibilityDate = "2026-08-22" }
}
