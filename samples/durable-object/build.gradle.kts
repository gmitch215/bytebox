plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.CounterWorker"
	durableObjects("com.example.Counter")

	wrangler {
		name = "durable-object"
		compatibilityDate = "2026-08-22"
	}
}
