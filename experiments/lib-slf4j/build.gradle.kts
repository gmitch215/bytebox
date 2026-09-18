plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.slf4j:slf4j-api:2.0.16")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-slf4j"; compatibilityDate = "2026-08-22" }
}
