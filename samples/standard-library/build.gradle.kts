plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.StandardLibraryWorker"

	wrangler {
		name = "standard-library"
		compatibilityDate = "2026-08-22"
	}
}
