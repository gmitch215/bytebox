plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.JsBodyWorker"

	wrangler {
		name = "jsbody"
		compatibilityDate = "2026-08-22"
	}
}
