plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.HelloWorker"

	wrangler {
		name = "hello-world"
		compatibilityDate = "2026-08-22"
	}
}
