pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
	includeBuild("..")
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

includeBuild("..")

rootProject.name = "bytebox-samples"

include(
	"hello-world",
	"kv-counter",
	"durable-object",
	"cron",
	"email-router",
	"queue-consumer",
	"npm-dependency",
	"tcp-client",
	"standard-library"
)
