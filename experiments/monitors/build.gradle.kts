plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.MonitorWorker"
	wrangler { name = "monitors"; compatibilityDate = "2026-08-22" }
}
