plugins {
	id("dev.gmitch215.bytebox")
}

dependencies {
	implementation("com.google.guava:guava:33.4.0-jre")
}

bytebox {
	handlerClass = "com.example.MixedWorker"

	wrangler {
		name = "mixed-dependencies"
		compatibilityDate = "2026-08-22"
	}

	npm("nanoid", "^5.0.9")
}
