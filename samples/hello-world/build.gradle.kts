plugins {
	java
	id("dev.gmitch215.bytebox") version "1.0.0"
}

repositories { mavenCentral() }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies { implementation("dev.gmitch215:bytebox-core:1.0.0") }

bytebox {
	handlerClass = "com.example.HelloWorker"
	workerName = "hello-world"
}
