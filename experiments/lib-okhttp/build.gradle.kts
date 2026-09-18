plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-okhttp"; compatibilityDate = "2026-08-22" }
}
