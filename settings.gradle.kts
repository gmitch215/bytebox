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
