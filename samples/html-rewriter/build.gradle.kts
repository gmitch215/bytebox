plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.RewriteWorker"

	wrangler {
		name = "html-rewriter"
		compatibilityDate = "2026-08-22"
	}
}
