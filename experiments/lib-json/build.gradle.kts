plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.json:json:20250107")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-json"; compatibilityDate = "2026-08-22" }
}
