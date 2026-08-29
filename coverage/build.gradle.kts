description = "Java coverage recorded inside workerd"

val tool by sourceSets.creating

dependencies {
	// the probe holder is compiled into the instrumented module, so it is written against the same
	// interop surface everything else on the wasm side is
	api(libs.teavm.jso)

	"toolImplementation"(libs.jacoco.core)
	"toolImplementation"(libs.jacoco.report)
}

val core = project(":bytebox-core")

val instrumentCore by tasks.registering(JavaExec::class) {
	description = "Rewrites the compiled core with JaCoCo's probes, for the workerd lane"
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	dependsOn(core.tasks.named("classes"))

	classpath = tool.runtimeClasspath
	mainClass.set("dev.gmitch215.bytebox.coverage.Instrument")

	val from = core.layout.buildDirectory.dir("classes/java/main")
	val to = layout.buildDirectory.dir("instrumented")
	inputs.dir(from)
	outputs.dir(to)
	argumentProviders.add(
		CommandLineArgumentProvider {
			listOf(from.get().asFile.absolutePath, to.get().asFile.absolutePath)
		}
	)
}

val workersCoverageReport by tasks.registering(JavaExec::class) {
	description = "Turns what the workerd lane recorded into a report the JVM lane's uploader takes"
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	dependsOn(core.tasks.named("classes"))

	classpath = tool.runtimeClasspath
	mainClass.set("dev.gmitch215.bytebox.coverage.Report")

	val dump = rootProject.layout.projectDirectory.file("npm/coverage/workers-probes.txt")
	val classes = core.layout.buildDirectory.dir("classes/java/main")
	val sources = core.layout.projectDirectory.dir("src/main/java")
	val report = core.layout.buildDirectory.file("jacoco-workers.xml")

	onlyIf("the workerd lane has to have run first") { dump.asFile.isFile }
	argumentProviders.add(
		CommandLineArgumentProvider {
			listOf(
				dump.asFile.absolutePath,
				classes.get().asFile.absolutePath,
				sources.asFile.absolutePath,
				report.get().asFile.absolutePath
			)
		}
	)
}
