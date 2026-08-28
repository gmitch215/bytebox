plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.TcpWorker"

	wrangler {
		name = "tcp-client"
		compatibilityDate = "2026-08-22"
	}
}
