plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("commons-codec:commons-codec:1.17.1")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-codec"; compatibilityDate = "2026-08-22" }
}
