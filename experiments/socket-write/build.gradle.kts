plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.SocketWriteWorker"

	wrangler {
		name = "bb103-b11-socket-write"
		compatibilityDate = "2026-08-22"
	}
}
