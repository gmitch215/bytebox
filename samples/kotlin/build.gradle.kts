plugins {
	id("dev.gmitch215.bytebox")
	kotlin("jvm") version "2.4.20"
}

bytebox {
	handlerClass = "com.example.KotlinWorker"

	wrangler {
		name = "kotlin"
		compatibilityDate = "2026-08-22"
	}
}
