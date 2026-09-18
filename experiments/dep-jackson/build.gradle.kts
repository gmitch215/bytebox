plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

bytebox {
	handlerClass = "com.example.JacksonWorker"
	wrangler { name = "dep-jackson"; compatibilityDate = "2026-08-22" }
}
