plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.WrapperWorker"

	wrangler {
		name = "wrappers"
		compatibilityDate = "2026-08-22"
	}
}
