plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.ConformanceWorker"
	wrangler { name = "conformance"; compatibilityDate = "2026-08-22" }
}
