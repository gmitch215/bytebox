plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.apache.logging.log4j:log4j-core:2.24.3")
}

bytebox {
	handlerClass = "com.example.Log4jWorker"
	wrangler { name = "dep-log4j"; compatibilityDate = "2026-08-22" }
}
