plugins {
	id("dev.gmitch215.bytebox")
}

bytebox {
	handlerClass = "com.example.OrderConsumer"

	wrangler {
		name = "queue-consumer"
		compatibilityDate = "2026-08-22"
		queueConsumers("orders")
	}

	bindings {
		queue("QUEUE", "orders")
		d1()
	}
}
