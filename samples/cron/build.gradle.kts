plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.NightlyWorker"

	wrangler {
		name = "cron"
		compatibilityDate = "2026-08-22"
		crons("0 3 * * *")
	}

	bindings {
		kv()
	}
}
