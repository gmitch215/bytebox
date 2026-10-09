plugins {
	id("dev.gmitch215.bytebox")
}

val cell = (findProperty("cell") as String?) ?: "B"
val prog = (findProperty("prog") as String?) ?: "semaphore"

sourceSets.main {
	java.srcDir("src/cells/$cell/java")
	resources.srcDir("src/cells/$cell/resources")
	if (cell !in listOf("A", "P", "R", "F", "G", "H", "I", "J")) java.srcDir("src/$prog/java")
}

dependencies {
	compileOnly("org.teavm:teavm-extension-spi:0.15.0")
}

bytebox {
	handlerClass = "com.example.Contend"
	if (prog == "durable") {
		durableObjects("com.example.Room", "com.example.Fragile")
		workflows("com.example.Flow")
	}
	wrangler { name = "lock-delegation"; compatibilityDate = "2026-08-22" }
}

if (findProperty("names") == "true") {
	teavm {
		wasmGC {
			obfuscated = false
			debugInformation = true
		}
	}
}
