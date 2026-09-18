plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("joda-time:joda-time:2.13.0")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-joda"; compatibilityDate = "2026-08-22" }
}
