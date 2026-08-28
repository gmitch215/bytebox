plugins {
	id("com.vanniktech.maven.publish")
	signing
}

description = "Worker entry points and Cloudflare bindings for Java"

dependencies {
	api(libs.teavm.jso)
	api(libs.teavm.jso.apis)
	compileOnly(libs.annotations)

	testImplementation(libs.junit.api)
	testImplementation(libs.junit.params)
	testRuntimeOnly(libs.junit.engine)
}

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
