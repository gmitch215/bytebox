plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.ProbeWorker"
	wrangler { name = "probe"; compatibilityDate = "2026-08-22" }
}

teavm {
	wasmGC {
		obfuscated = false
		debugInformation = true
	}
}
