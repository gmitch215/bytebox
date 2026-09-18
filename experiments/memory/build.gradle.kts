plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.MemoryWorker"

	wrangler {
		name = "memory"
		compatibilityDate = "2026-08-22"
	}
}

teavm {
	wasmGC {
		minDirectBuffersSize.set((project.findProperty("mdbs") as String? ?: "1").toInt())
	}
}
