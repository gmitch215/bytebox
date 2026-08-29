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

val functionalTest by sourceSets.creating

gradlePlugin.testSourceSets.add(functionalTest)

dependencies {
	implementation(gradleApi())
	implementation(libs.teavm.tooling)
	implementation(libs.teavm.core)
	// this plugin applies org.teavm, so it has to be resolvable from this plugin's own classpath
	implementation(libs.teavm.gradle.plugin)

	testImplementation(gradleTestKit())
	testImplementation(libs.junit.api)
	testImplementation(libs.junit.params)
	testRuntimeOnly(libs.junit.engine)
	// the generators read a handler's own interfaces and annotations, so the tests need real classes
	// implementing them rather than names; this is the same surface a consumer compiles against
	testImplementation(project(":bytebox-core"))

	"functionalTestImplementation"(gradleTestKit())
	"functionalTestImplementation"(libs.junit.api)
	"functionalTestImplementation"(libs.junit.params)
	"functionalTestRuntimeOnly"(libs.junit.engine)
	"functionalTestRuntimeOnly"(rootProject.libs.junit.launcher)
}

tasks.register<Test>("functionalTest") {
	description = "Drives the plugin against real sample projects"
	group = "verification"
	testClassesDirs = functionalTest.output.classesDirs
	classpath = functionalTest.runtimeClasspath
	useJUnitPlatform()

	// the version under test is not published anywhere a resolver could find it, so the fixture
	// projects put it on their compile classpath by path. The path is captured as a provider rather
	// than as the task, which the configuration cache cannot serialise.
	val coreJar = project(":bytebox-core").tasks.named<Jar>("jar").flatMap { it.archiveFile }
	val corePath = coreJar.map { it.asFile.absolutePath }
	inputs.file(coreJar)
	jvmArgumentProviders.add(
		CommandLineArgumentProvider { listOf("-Dbytebox.core.jars=" + corePath.get()) }
	)
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
