plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("com.google.code.gson:gson:2.11.0")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-gson"; compatibilityDate = "2026-08-22" }
}
