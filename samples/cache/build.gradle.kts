plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.CachedWorker"

	wrangler {
		name = "cache"
		compatibilityDate = "2026-08-22"
	}
}
