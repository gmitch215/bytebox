plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.CounterWorker"

	wrangler {
		name = "kv-counter"
		compatibilityDate = "2026-08-22"
	}

	bindings {
		kv()
	}
}
