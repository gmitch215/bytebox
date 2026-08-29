plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bytebox"

include(":bytebox-core")
project(":bytebox-core").projectDir = rootDir.resolve("core")

include(":bytebox-plugin")
project(":bytebox-plugin").projectDir = rootDir.resolve("plugin")

include(":bytebox-size")
project(":bytebox-size").projectDir = rootDir.resolve("size")

include(":bytebox-coverage")
project(":bytebox-coverage").projectDir = rootDir.resolve("coverage")

include(":bytebox-coverage-fixture")
project(":bytebox-coverage-fixture").projectDir = rootDir.resolve("coverage/fixture")

include(":bytebox-fixture")
project(":bytebox-fixture").projectDir = rootDir.resolve("coverage/plain")
