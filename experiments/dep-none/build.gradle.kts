plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.NoneWorker"
	wrangler { name = "dep-none"; compatibilityDate = "2026-08-22" }
}
