plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.MailRouter"

	wrangler {
		name = "email-router"
		compatibilityDate = "2026-08-22"
	}

	bindings {
		kv()
	}
}
