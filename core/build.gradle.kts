plugins {
	id("com.vanniktech.maven.publish")
	signing
}

description = "Worker entry points and Cloudflare bindings for Java"

dependencies {
	api(libs.teavm.jso)
	api(libs.teavm.jso.apis)
	// java.time is absent from the class library, so this supplies it; not api, because a project
	// writes java.time and the substitution rewrites it, and org.threeten.bp is never typed by hand
	implementation(libs.threetenbp)
	// the substitution policy runs in the compiler, not in the program
	compileOnly(libs.teavm.extension.spi)
	compileOnly(libs.annotations)

	testImplementation(libs.junit.api)
	testImplementation(libs.junit.params)
	testRuntimeOnly(libs.junit.engine)
	// the substitution policies are ordinary classes, so which names they select is checkable here
	testImplementation(libs.teavm.extension.spi)
}

// the same suite again with a carriage-return line separator. %n is the platform's separator rather
// than a newline, so a host that uses a newline cannot tell a correct implementation from one that
// hardcoded it, and the difference only ever surfaced on Windows
val tests = project.the<SourceSetContainer>()["test"]

val testCrlf by tasks.registering(Test::class) {
	description = "Runs the tests as a host whose line separator is a carriage-return pair"
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	testClassesDirs = tests.output.classesDirs
	classpath = tests.runtimeClasspath
	jvmArgs("-Dline.separator=\r\n")
}

tasks.named("check") { dependsOn(testCrlf) }

mavenPublishing {
	coordinates(project.group.toString(), project.name, project.version.toString())

	pom {
		name.set("bytebox-core")
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
