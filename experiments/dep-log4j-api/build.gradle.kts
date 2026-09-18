plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.apache.logging.log4j:log4j-api:2.24.3")
}

bytebox {
	handlerClass = "com.example.Log4jApiWorker"
	wrangler { name = "dep-log4j-api"; compatibilityDate = "2026-08-22" }
}
