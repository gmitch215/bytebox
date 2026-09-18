plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("com.google.guava:guava:33.4.0-jre")
}

bytebox {
	handlerClass = "com.example.GuavaWorker"
	wrangler { name = "dep-guava"; compatibilityDate = "2026-08-22" }
}
