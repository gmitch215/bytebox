plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("org.yaml:snakeyaml:2.3")
}

bytebox {
	handlerClass = "com.example.LibWorker"
	wrangler { name = "lib-snakeyaml"; compatibilityDate = "2026-08-22" }
}
