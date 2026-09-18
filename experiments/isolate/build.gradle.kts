plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.IsolateWorker"
	wrangler { name = "bytebox-isolate"; compatibilityDate = "2026-08-22" }
}
