plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.OrderWorker"
	workflows("com.example.OrderFlow")

	wrangler {
		name = "workflow"
		compatibilityDate = "2026-08-22"
	}

	bindings {
		workflow("com.example.OrderFlow", "WORKFLOW", "order-flow")
	}
}
