plugins {
	base
}

subprojects {
	repositories { mavenCentral() }

	plugins.withId("java") {
		extensions.configure<JavaPluginExtension> {
			toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
		}

		dependencies {
			"implementation"("dev.gmitch215:bytebox-core")
			"implementation"("org.teavm:teavm-jso:0.15.0")
			"implementation"("org.teavm:teavm-jso-apis:0.15.0")
		}
	}
}

tasks.register("buildWorkers") {
	group = "bytebox"
	description = "Builds every sample, which is also the end-to-end check for the plugin"
	dependsOn(subprojects.map { "${it.path}:buildWorker" })
}

tasks.named("build") { dependsOn("buildWorkers") }
