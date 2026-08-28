plugins {
	id("com.gradle.plugin-publish")
	id("com.vanniktech.maven.publish")
	signing
}

description = "Compiles a Java workspace into a Cloudflare Worker"

gradlePlugin {
	website.set("https://github.com/gmitch215/bytebox")
	vcsUrl.set("https://github.com/gmitch215/bytebox.git")

	plugins {
		register("bytebox") {
			id = "dev.gmitch215.bytebox"
			displayName = "bytebox"
			description = "Compiles a Java workspace into a Cloudflare Worker"
			implementationClass = "dev.gmitch215.bytebox.gradle.ByteboxPlugin"
			tags = listOf("cloudflare", "workers", "wasm", "webassembly", "java", "teavm")
		}
	}
}

dependencies {
	implementation(gradleApi())
	implementation(libs.teavm.tooling)
	implementation(libs.teavm.core)

	testImplementation(gradleTestKit())
	testImplementation(libs.junit.api)
	testImplementation(libs.junit.params)
	testRuntimeOnly(libs.junit.engine)
}

val functionalTest by sourceSets.creating

gradlePlugin.testSourceSets.add(functionalTest)

tasks.register<Test>("functionalTest") {
	description = "Drives the plugin against real sample projects"
	group = "verification"
	testClassesDirs = functionalTest.output.classesDirs
	classpath = functionalTest.runtimeClasspath
	useJUnitPlatform()
}

tasks.named("check") { dependsOn("functionalTest") }

mavenPublishing {
	coordinates(project.group.toString(), project.name, project.version.toString())

	pom {
		name.set("bytebox-plugin")
		description.set(project.description)
		url.set("https://github.com/gmitch215/bytebox")
		inceptionYear.set("2026")

		licenses {
			license {
				name.set("MIT License")
				url.set("https://opensource.org/licenses/MIT")
			}
		}

		developers {
			developer {
				id = "gmitch215"
				name = "Gregory Mitchell"
				email = "me@gmitch215.xyz"
			}
		}

		scm {
			connection = "scm:git:git://github.com/gmitch215/bytebox.git"
			developerConnection = "scm:git:ssh://github.com/gmitch215/bytebox.git"
			url = "https://github.com/gmitch215/bytebox"
		}
	}

	publishToMavenCentral(true)
	signAllPublications()
}
