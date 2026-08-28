plugins {
	id("com.gradle.plugin-publish") version "2.0.0" apply false
	id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

val v = "1.0.0"

allprojects {
	val base = if (project.hasProperty("snapshot")) "$v-SNAPSHOT" else v
	val suffix = project.findProperty("suffix")?.toString()?.takeIf { it.isNotBlank() }

	group = "dev.gmitch215"
	version = if (suffix != null) "$base-$suffix" else base

	repositories {
		mavenCentral()
		mavenLocal()
	}
}

subprojects {
	apply(plugin = "java-library")
	apply(plugin = "jacoco")

	configure<JavaPluginExtension> {
		toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
		withSourcesJar()
		withJavadocJar()
	}

	tasks.withType<JavaCompile>().configureEach {
		options.encoding = "UTF-8"
		options.release.set(21)
	}

	tasks.withType<Javadoc>().configureEach {
		options {
			require(this is StandardJavadocDocletOptions)
			encoding = "UTF-8"
			addStringOption("Xdoclint:none", "-quiet")
			links("https://docs.oracle.com/en/java/javase/21/docs/api/")
		}
	}

	dependencies {
		// junit 6 puts the launcher on the runtime classpath rather than shipping it with the engine
		"testRuntimeOnly"(rootProject.libs.junit.launcher)
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
		testLogging { events("passed", "skipped", "failed") }
		finalizedBy(tasks.withType<JacocoReport>())
	}

	// vanniktech handles Maven Central and signing; GitHub Packages is a second repository
	plugins.withId("maven-publish") {
		configure<PublishingExtension> {
			repositories {
				maven {
					name = "GitHubPackages"
					url = uri("https://maven.pkg.github.com/gmitch215/bytebox")
					credentials {
						username = System.getenv("GITHUB_ACTOR")
						password = System.getenv("GITHUB_TOKEN")
					}
				}
			}
		}
	}

	plugins.withId("signing") {
		configure<SigningExtension> {
			val signingKey: String? by project
			val signingPassword: String? by project
			if (signingKey != null && signingPassword != null) {
				useInMemoryPgpKeys(signingKey, signingPassword)
			}
		}
	}

	tasks.withType<JacocoReport>().configureEach {
		dependsOn(tasks.withType<Test>())
		reports {
			csv.required.set(false)
			xml.required.set(true)
			xml.outputLocation.set(layout.buildDirectory.file("jacoco.xml"))
			html.required.set(true)
			html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
		}
	}
}

description = "Java on Cloudflare Workers"

// aggregate javadoc for the two published modules, deployed by javadoc.sh
val published = listOf(":bytebox-core", ":bytebox-plugin")

tasks.register<Javadoc>("allJavadoc") {
	published.forEach { dependsOn("$it:javadoc") }

	title = "bytebox $version API"
	setDestinationDir(layout.buildDirectory.dir("docs/javadoc").get().asFile)
	val mains = published.map { project(it).the<SourceSetContainer>()["main"] }
	source = files(mains.map { it.allJava }).asFileTree
	classpath = files(mains.map { it.compileClasspath })

	options {
		require(this is StandardJavadocDocletOptions)

		encoding = "UTF-8"
		addStringOption("Xdoclint:none", "-quiet")
		links("https://docs.oracle.com/en/java/javase/21/docs/api/")
	}
}
