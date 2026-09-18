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
