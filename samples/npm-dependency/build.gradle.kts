plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.IdWorker"

	wrangler {
		name = "npm-dependency"
		compatibilityDate = "2026-08-22"
	}

	npm("nanoid", "^5.0.9")
}
