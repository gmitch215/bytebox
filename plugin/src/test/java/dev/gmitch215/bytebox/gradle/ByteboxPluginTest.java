package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.teavm.gradle.api.OptimizationLevel;
import org.teavm.gradle.api.TeaVMExtension;

@DisplayName("applying the plugin")
class ByteboxPluginTest {

	@TempDir
	Path directory;

	private Project project;
	private ByteboxExtension bytebox;

	@BeforeEach
	void apply() {
		project = Projects.project(directory);
		project.getPlugins().apply(ByteboxPlugin.class);
		bytebox = project.getExtensions().getByType(ByteboxExtension.class);
	}

	@Test
	@DisplayName("brings the Java and compiler plugins with it")
	void appliesWhatItNeeds() {
		assertTrue(project.getPluginManager().hasPlugin("java"));
		assertTrue(project.getPluginManager().hasPlugin("org.teavm"));
	}

	@Test
	@DisplayName("registers every task the pipeline needs, in one group")
	void registersTheTasks() {
		for (String name : List.of(
			"generateEntryPoint",
			"generateCodecs",
			"generateSerialCodecs",
			"generateNpmBindings",
			"packWasm",
			"generateWorkerScaffold",
			"buildWorker",
			"sizeReport",
			"bindingsReport"
		)) {
			Task task = project.getTasks().getByName(name);
			assertEquals(ByteboxPlugin.GROUP, task.getGroup(), name + " is in the wrong group");
			assertNotNull(task.getDescription(), name + " has no description");
		}
	}

	@Test
	@DisplayName("wraps the Wrangler commands a project would otherwise leave Gradle for")
	void registersTheWranglerWrappers() {
		for (String name : List.of(
			"workerDev",
			"workerDeploy",
			"workerDryRun",
			"workerTail",
			"workerTypes",
			"workerVersionsUpload",
			"wrangler",
			"d1List",
			"d1Info",
			"d1Query",
			"d1Migrate",
			"d1MigrationsList",
			"kvList",
			"kvGet",
			"kvPut",
			"kvBulkPut",
			"r2List",
			"r2Put",
			"aiModels",
			"aiFinetunes",
			"queueList",
			"queueInfo",
			"secretList",
			"hyperdriveList",
			"vectorizeList",
			"workflowsList",
			"versionsList"
		)) {
			assertNotNull(project.getTasks().getByName(name), name + " is missing");
		}
	}

	@Test
	@DisplayName("marks the commands that write, so they need confirming")
	void marksTheDestructiveOnes() {
		assertTrue(wrangler("workerDeploy").getDestructive().get());
		assertTrue(wrangler("kvPut").getDestructive().get());
		assertTrue(wrangler("d1Migrate").getDestructive().get());
		assertFalse(wrangler("kvGet").getDestructive().get());
		assertFalse(wrangler("workerDryRun").getDestructive().get());
	}

	@Test
	@DisplayName("marks the commands that reach the account, and leaves the local ones alone")
	void marksTheAccountOnes() {
		assertTrue(wrangler("workerDeploy").getRequiresAccount().get());
		assertTrue(wrangler("secretList").getRequiresAccount().get());
		assertFalse(wrangler("workerDev").getRequiresAccount().get());
		assertFalse(wrangler("workerDryRun").getRequiresAccount().get());
	}

	@Test
	@DisplayName("builds the Worker first only for the commands that read it")
	void buildsBeforeTheCommandsThatNeedIt() {
		assertTrue(dependsOnBuild("workerDev"));
		assertTrue(dependsOnBuild("workerDeploy"));
		assertTrue(dependsOnBuild("workerDryRun"));
		assertTrue(dependsOnBuild("workerTypes"));
		assertTrue(dependsOnBuild("workerVersionsUpload"));
		assertFalse(dependsOnBuild("kvList"));
		assertFalse(dependsOnBuild("workerTail"));
	}

	@Test
	@DisplayName("names the Worker after the project and dates it today")
	void conventions() {
		assertEquals(project.getName(), bytebox.getWrangler().getName().get());
		assertEquals(
			LocalDate.now(ZoneOffset.UTC).toString(),
			bytebox.getWrangler().getCompatibilityDate().get()
		);
		assertTrue(bytebox.getWrangler().getObservability().get());
		assertEquals(SizeSpec.ModuleType.AUTO, bytebox.getSize().getModule().get());
		assertCompressionConventions();
		assertTrue(bytebox.getNPMIntrospection().get());
		assertTrue(bytebox.getCoreDependency().get());
		assertNotNull(bytebox.getCoreVersion().get());
	}

	@Test
	@DisplayName("pins the core and the npm package to the release it was built from")
	void followsItsOwnVersion() {
		String release = System.getProperty("bytebox.version");
		String line = release.replaceFirst("^(\\d+\\.\\d+)\\..*$", "^$1.0");

		assertEquals(release, bytebox.getCoreVersion().get());
		assertEquals(
			line,
			project
				.getTasks()
				.named("generateWorkerScaffold", GenerateScaffoldTask.class)
				.get()
				.getByteboxVersion()
				.get()
		);
	}

	@Test
	@DisplayName("sets the compiler options a Worker always wants")
	void compilerDefaults() {
		TeaVMExtension teavm = project.getExtensions().getByType(TeaVMExtension.class);

		assertTrue(teavm.getWasmGC().getModularRuntime().get());
		// zero leaves the interop's staging heap empty and the first byte[] crossing never returns
		assertTrue(teavm.getWasmGC().getMinDirectBuffersSize().get() >= 1);
		assertTrue(teavm.getWasmGC().getObfuscated().get());
		assertFalse(teavm.getWasmGC().getDebugInformation().get());
		assertFalse(teavm.getWasmGC().getSourceMap().get());
		assertEquals(OptimizationLevel.AGGRESSIVE, teavm.getWasmGC().getOptimization().get());
		assertEquals(
			GenerateEntryPointTask.PACKAGE + "." + GenerateEntryPointTask.CLASS_NAME,
			teavm.getAll().getMainClass().get()
		);
	}

	@Test
	@DisplayName("compiles the generated code in a source set of its own")
	void generatedSourceSet() {
		assertNotNull(
			project
				.getExtensions()
				.getByType(JavaPluginExtension.class)
				.getSourceSets()
				.getByName(ByteboxPlugin.GENERATED_SOURCE_SET)
		);
	}

	@Test
	@DisplayName("puts the npm bindings on the main source set, because a project calls them")
	void bindingsOnMain() {
		boolean generated =
			project
				.getExtensions()
				.getByType(JavaPluginExtension.class)
				.getSourceSets()
				.getByName("main")
				.getJava()
				.getSrcDirs()
				.stream()
				// compared as path elements rather than as text, because a separator is the host's
				.anyMatch(directory ->
					directory.toPath().endsWith(Path.of("generated", "sources", "bytebox", "npm"))
				);

		assertTrue(generated, "the generated bindings are not a main source directory");
	}

	@Test
	@DisplayName("adds bytebox to the classpath, so applying the plugin is the whole setup")
	void addsCore() {
		boolean present = project
			.getConfigurations()
			.getByName("implementation")
			.getDependencies()
			.stream()
			.anyMatch(dependency -> "bytebox-core".equals(dependency.getName()));

		assertTrue(present, "bytebox-core is not on the classpath");
	}

	@Test
	@DisplayName("leaves the classpath alone for a project that resolves bytebox itself")
	void coreCanBeTurnedOff() {
		Project other = Projects.project(directory.resolve("other"));
		other.getPlugins().apply(ByteboxPlugin.class);
		other.getExtensions().getByType(ByteboxExtension.class).getCoreDependency().set(false);

		assertTrue(
			other
				.getConfigurations()
				.getByName("implementation")
				.getDependencies()
				.stream()
				.noneMatch(dependency -> "bytebox-core".equals(dependency.getName()))
		);
	}

	// #region Kotlin

	@ParameterizedTest(name = "Kotlin applied {0}")
	@ValueSource(strings = { "before", "after" })
	@DisplayName("orders the generators around the Kotlin compile tasks, in either plugin order")
	void kotlinOrdering(String order) {
		Project kotlin = withKotlin(order);

		assertTrue(directly(kotlin, "compileKotlin", "generateNpmBindings"));
		for (String generator : List.of(
			"generateEntryPoint",
			"generateCodecs",
			"generateSerialCodecs"
		)) {
			assertTrue(directly(kotlin, "compileByteboxKotlin", generator), generator);
			assertTrue(directly(kotlin, generator, "compileKotlin"), generator);
		}
		assertTrue(directly(kotlin, "generateWorkerScaffold", "compileKotlin"));
	}

	@ParameterizedTest(name = "Kotlin applied {0}")
	@ValueSource(strings = { "before", "after" })
	@DisplayName("scans the Kotlin output, which is where a Kotlin handler and its types are")
	void kotlinOutputIsScanned(String order) {
		Project kotlin = withKotlin(order);
		File classes = kotlin
			.getLayout()
			.getBuildDirectory()
			.dir("classes/kotlin/main")
			.get()
			.getAsFile();

		GenerateCodecsTask codecs = (GenerateCodecsTask) kotlin
			.getTasks()
			.getByName("generateCodecs");
		GenerateSerialCodecsTask serial = (GenerateSerialCodecsTask) kotlin
			.getTasks()
			.getByName("generateSerialCodecs");

		assertTrue(codecs.getScanned().getFiles().contains(classes));
		assertTrue(serial.getScanned().getFiles().contains(classes));
	}

	@Test
	@DisplayName("adds nothing Kotlin to a Java project")
	void javaIsUnaffected() {
		for (Task task : project.getTasks()) {
			assertTrue(task.getName().indexOf("Kotlin") < 0, task.getName());
			assertTrue(
				task
					.getDependsOn()
					.stream()
					.noneMatch(entry -> named(entry).contains("Kotlin")),
				task.getName()
			);
		}
	}

	private Project withKotlin(String order) {
		Project other = Projects.project(directory.resolve("kotlin-" + order));
		if (order.equals("before")) other.getPlugins().apply("org.jetbrains.kotlin.jvm");
		other.getPlugins().apply(ByteboxPlugin.class);
		if (order.equals("after")) other.getPlugins().apply("org.jetbrains.kotlin.jvm");
		return other;
	}

	/** Reads the task's own dependsOn, because resolving the classpath needs a repository. */
	private static boolean directly(Project target, String task, String dependency) {
		return target
			.getTasks()
			.getByName(task)
			.getDependsOn()
			.stream()
			.anyMatch(entry -> named(entry).equals(dependency));
	}

	private static String named(Object dependency) {
		if (dependency instanceof Task task) return task.getName();
		if (dependency instanceof TaskProvider<?> provider) return provider.getName();
		return String.valueOf(dependency);
	}

	// #endregion

	// #region the extension

	@Test
	@DisplayName("collects the types and packages a project names")
	void extensionLists() {
		bytebox.jsonTypes("com.example.A", "com.example.B");
		bytebox.jsonTypes("com.example.C");
		bytebox.serialTypes("com.example.D");
		bytebox.durableObjects("com.example.Counter");
		bytebox.npm("nanoid", "^5.0.9");
		bytebox.npmBindings("nanoid");

		assertEquals(
			List.of("com.example.A", "com.example.B", "com.example.C"),
			bytebox.getJSONTypes().get()
		);
		assertEquals(List.of("com.example.D"), bytebox.getSerialTypes().get());
		assertEquals(List.of("com.example.Counter"), bytebox.getDurableObjectClasses().get());
		assertEquals(List.of("nanoid@^5.0.9"), bytebox.getNPMPackages().get());
		assertEquals(List.of("nanoid"), bytebox.getNPMBindingPackages().get());
	}

	@Test
	@DisplayName("configures the nested blocks the same way the build file does")
	void extensionBlocks() {
		bytebox.size(size -> size.getBudget().set("250KiB"));
		bytebox.wrangler(wrangler -> {
			wrangler.routes("example.com/*", "www.example.com/*");
			wrangler.crons("*/5 * * * *");
			wrangler.compatibilityFlags("nodejs_compat");
			wrangler.tailConsumers("logger");
		});
		bytebox.bindings(bindings -> bindings.kv());
		bytebox.bindings(BindingType.D1, BindingType.R2);

		assertEquals(250 * 1024, bytebox.getSize().budgetBytes());
		assertEquals(
			List.of("example.com/*", "www.example.com/*"),
			bytebox.getWrangler().getRoutes().get()
		);
		assertEquals(List.of("*/5 * * * *"), bytebox.getWrangler().getCrons().get());
		assertEquals(List.of("nodejs_compat"), bytebox.getWrangler().getCompatibilityFlags().get());
		assertEquals(List.of("logger"), bytebox.getWrangler().getTailConsumers().get());
		assertEquals(
			List.of("KV", "DB", "BLOB"),
			bytebox.getBindings().getAll().stream().map(Binding::name).toList()
		);
	}

	@Test
	@DisplayName("keeps every class library extra off until a project switches it on")
	void classlibDefaults() {
		assertFalse(bytebox.getClasslib().getZoneHistory().get());
		assertFalse(entryPoint().getZoneHistory().get());
		assertFalse(bytebox.getClasslib().getDateFormat().get());
		assertFalse(entryPoint().getDateFormat().get());
	}

	@Test
	@DisplayName("hands the classlib block to the entry point generator")
	void classlibBlock() {
		bytebox.classlib(classlib -> classlib.getZoneHistory().set(true));

		assertTrue(bytebox.getClasslib().getZoneHistory().get());
		assertTrue(entryPoint().getZoneHistory().get());
		assertFalse(entryPoint().getDateFormat().get());

		bytebox.classlib(classlib -> classlib.getDateFormat().set(true));

		assertTrue(entryPoint().getDateFormat().get());
	}

	@Test
	@DisplayName("passes compressor arguments through")
	@SuppressWarnings("removal")
	void compressionArgs() {
		bytebox.getSize().compressionArgs("--long=27");
		bytebox.getSize().compressionArgs("--ultra");

		assertEquals(List.of("--long=27", "--ultra"), bytebox.getSize().getCompressionArgs().get());
	}

	@SuppressWarnings("removal")
	private void assertCompressionConventions() {
		assertEquals(SizeSpec.Compressor.ZSTD, bytebox.getSize().getCompression().get());
		assertEquals(22, bytebox.getSize().getCompressionLevel().get());
	}

	// #endregion

	private GenerateEntryPointTask entryPoint() {
		return project.getTasks().named("generateEntryPoint", GenerateEntryPointTask.class).get();
	}

	private WranglerTask wrangler(String name) {
		return (WranglerTask) project.getTasks().getByName(name);
	}

	private boolean dependsOnBuild(String name) {
		return project
			.getTasks()
			.getByName(name)
			.getDependsOn()
			.stream()
			.anyMatch(dependency -> "buildWorker".equals(String.valueOf(dependency)));
	}
}
