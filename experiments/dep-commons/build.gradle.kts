plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.apache.commons:commons-lang3:3.17.0")
}

bytebox {
	handlerClass = "com.example.CommonsWorker"
	wrangler { name = "dep-commons"; compatibilityDate = "2026-08-22" }
}
