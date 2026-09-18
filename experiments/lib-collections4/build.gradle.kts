plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.apache.commons:commons-collections4:4.4")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-collections4"; compatibilityDate = "2026-08-22" }
}
