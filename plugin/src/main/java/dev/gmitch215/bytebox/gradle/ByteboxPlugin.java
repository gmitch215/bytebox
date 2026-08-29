package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.teavm.gradle.api.OptimizationLevel;
import org.teavm.gradle.api.TeaVMExtension;
import org.teavm.gradle.api.TeaVMWasmGCConfiguration;
import org.teavm.gradle.tasks.TeaVMTask;

/**
 * Compiles a Java workspace into a Cloudflare Worker.
 *
 * <p>Applying this plugin registers the {@code bytebox { }} block and a pipeline that ends in a
 * deployable Worker:
 *
 * <ol>
 *   <li>{@code generateNpmBindings} writes Java for the npm packages a project names, read from their
 *       TypeScript types.
 *   <li>{@code generateEntryPoint} writes the class the module exports its handlers from, deriving
 *       which handlers from the interfaces the handler class implements.
 *   <li>{@code generateCodecs} writes a JSON codec for every type annotated {@code @JSONType}.
 *   <li>{@code compileWasm} runs the compiler, which is TeaVM's own task.
 *   <li>{@code packWasm} puts the module where the Worker imports it, and enforces the size budget.
 *   <li>{@code generateWorkerScaffold} writes the Wrangler configuration, the JavaScript entry point
 *       and the package manifest.
 *   <li>{@code buildWorker} depends on all of it.
 * </ol>
 *
 * <p>Everything up to and including {@code buildWorker} runs with no Cloudflare credentials. Only the
 * commands that reach the account need a login, and each says so.
 *
 * @since 1.0.0
 */
public class ByteboxPlugin implements Plugin<Project> {

	/** The task group every task this plugin registers belongs to. */
	public static final String GROUP = "bytebox";

	/** Where the generated Worker project is written, under the build directory. */
	public static final String WORKER_DIRECTORY = "bytebox/worker";

	/** The source set the generated entry point and codecs are compiled in. */
	public static final String GENERATED_SOURCE_SET = "bytebox";

	/** The Java package generated npm bindings live in. */
	public static final String NPM_PACKAGE = "dev.gmitch215.bytebox.npm";

	/**
	 * Megabytes of linear memory the interop stages arrays through, which cannot be zero.
	 *
	 * <p>TeaVM's own default is two. One is enough for the staging buffer, which is four kilobytes,
	 * and the heap grows from there when a program needs more.
	 */
	public static final int BUFFER_HEAP_MEGABYTES = 1;

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply("java");
		project.getPluginManager().apply("org.teavm");

		ByteboxExtension extension = project
			.getExtensions()
			.create("bytebox", ByteboxExtension.class);
		conventions(project, extension);
		teavmDefaults(project);
		coreDependency(project, extension);

		Directory buildDirectory = project.getLayout().getBuildDirectory().get();
		File workerDirectory = buildDirectory.dir(WORKER_DIRECTORY).getAsFile();
		File generatedSources = buildDirectory.dir("generated/sources/bytebox/java").getAsFile();
		File generatedBindings = buildDirectory.dir("generated/sources/bytebox/npm").getAsFile();

		// a source set of its own, not `main`. Deciding what to generate means reading which trigger
		// interfaces the handler implements, which needs the handler compiled — and putting the
		// generated code in `main` would make compiling it depend on itself.
		SourceSetContainer sourceSets = project
			.getExtensions()
			.getByType(JavaPluginExtension.class)
			.getSourceSets();
		SourceSet main = sourceSets.getByName("main");

		// the npm bindings are the one generated thing that goes on `main`: a project calls them, so
		// they have to be compiled before the code that calls them rather than after
		main.getJava().srcDir(generatedBindings);
		TaskProvider<GenerateNPMBindingsTask> bindings = registerNPMBindings(
			project,
			extension,
			generatedBindings
		);
		project
			.getTasks()
			.named("compileJava")
			.configure(compile -> compile.dependsOn(bindings));

		SourceSet generated = sourceSets.create(GENERATED_SOURCE_SET, set -> {
			set.getJava().setSrcDirs(List.of(generatedSources));
			set.setCompileClasspath(main.getOutput().plus(main.getCompileClasspath()));
			set.setRuntimeClasspath(set.getOutput().plus(main.getRuntimeClasspath()));
		});

		// reading a class means resolving what it implements, so the interfaces have to be reachable
		// too: the project's own output alone is not enough
		FileCollection handlerClasspath = main.getOutput().plus(main.getCompileClasspath());

		TaskProvider<GenerateEntryPointTask> entryPoint = registerEntryPoint(
			project,
			extension,
			generatedSources,
			handlerClasspath
		);
		TaskProvider<GenerateCodecsTask> codecs = registerCodecs(
			project,
			extension,
			generatedSources,
			main,
			handlerClasspath
		);

		TaskProvider<GenerateSerialCodecsTask> serial = registerSerialCodecs(
			project,
			extension,
			generatedSources,
			main,
			handlerClasspath
		);

		project
			.getTasks()
			.named(generated.getCompileJavaTaskName())
			.configure(compile -> compile.dependsOn(entryPoint, codecs, serial));

		// the compiler needs both halves on its classpath, and its entry point is the generated one
		project
			.getTasks()
			.withType(TeaVMTask.class)
			.configureEach(task -> {
				task.getClasspath().from(generated.getOutput());
				task.dependsOn(generated.getCompileJavaTaskName());
			});
		project
			.getExtensions()
			.getByType(TeaVMExtension.class)
			.getAll()
			.getMainClass()
			.convention(GenerateEntryPointTask.PACKAGE + "." + GenerateEntryPointTask.CLASS_NAME);

		TaskProvider<PackWasmTask> pack = registerPack(project, extension, workerDirectory);
		TaskProvider<GenerateScaffoldTask> scaffold = registerScaffold(
			project,
			extension,
			workerDirectory,
			handlerClasspath
		);

		project.getTasks().register("buildWorker", task -> {
			task.setGroup(GROUP);
			task.setDescription("Builds the complete Worker, ready to deploy");
			task.dependsOn(pack, scaffold);
		});

		registerSizeReport(project, extension);
		registerWranglerTasks(project, extension, workerDirectory);
	}

	/**
	 * Sets the compiler options a Worker always wants, so a project does not carry them.
	 *
	 * <p>{@code modularRuntime} is not a preference: the loader imports the generated runtime as an ES
	 * module, and without it the runtime is a global assignment instead. Neither is
	 * {@code minDirectBuffersSize}, which is measured in megabytes and names more than direct byte
	 * buffers: it sizes the linear-memory heap the interop stages every {@code byte[]} through on its
	 * way to a typed array. At zero the heap is initialised empty and the first such conversion spins
	 * inside the allocator forever, so one megabyte is the floor rather than a budget.
	 *
	 * <p>The rest are conventions a project can override.
	 */
	private void teavmDefaults(Project project) {
		TeaVMWasmGCConfiguration wasm = project
			.getExtensions()
			.getByType(TeaVMExtension.class)
			.getWasmGC();
		wasm.getModularRuntime().set(true);
		wasm.getMinDirectBuffersSize().convention(BUFFER_HEAP_MEGABYTES);
		wasm.getObfuscated().convention(true);
		wasm.getDebugInformation().convention(false);
		wasm.getSourceMap().convention(false);
		wasm.getOptimization().convention(OptimizationLevel.AGGRESSIVE);
	}

	/**
	 * Puts bytebox on the project's classpath, so applying the plugin is the whole setup.
	 *
	 * <p>The version matches the plugin's own, because the two are released together and a project
	 * that mixed them would be compiling against a surface the generators do not write. A project can
	 * still pin its own by setting {@code coreVersion}, and declaring the dependency by hand wins: the
	 * version is a convention, and a resolution strategy or a platform constraint overrides it the way
	 * it overrides any other.
	 */
	private void coreDependency(Project project, ByteboxExtension extension) {
		extension.getCoreVersion().convention(version());
		extension.getCoreDependency().convention(true);

		// resolved lazily, because the bytebox block that can turn this off runs after the plugin is
		// applied and reading the flag here would always read the convention
		Provider<List<Dependency>> core = project.provider(() ->
			extension.getCoreDependency().get()
				? List.of(
						project
							.getDependencies()
							.create(
								"dev.gmitch215:bytebox-core:" + extension.getCoreVersion().get()
							)
					)
				: List.of()
		);
		project
			.getConfigurations()
			.named("implementation")
			.configure(configuration -> configuration.getDependencies().addAllLater(core));
	}

	/** The plugin's own version, which is the one its generated code is written against. */
	private static String version() {
		String implementation = ByteboxPlugin.class.getPackage().getImplementationVersion();
		return implementation == null ? "1.0.0" : implementation;
	}

	private void conventions(Project project, ByteboxExtension extension) {
		extension.getWrangler().getName().convention(project.getName());
		extension
			.getWrangler()
			.getCompatibilityDate()
			.convention(LocalDate.now(ZoneOffset.UTC).toString());
		extension.getWrangler().getObservability().convention(true);
		extension.getSize().getModule().convention(SizeSpec.ModuleType.AUTO);
		extension.getSize().getCompression().convention(SizeSpec.Compressor.ZSTD);
		extension.getSize().getCompressionLevel().convention(22);
		extension.getNPMIntrospection().convention(true);
	}

	private TaskProvider<GenerateEntryPointTask> registerEntryPoint(
		Project project,
		ByteboxExtension extension,
		File generatedSources,
		FileCollection handlerClasspath
	) {
		return project
			.getTasks()
			.register("generateEntryPoint", GenerateEntryPointTask.class, task -> {
				task.setGroup(GROUP);
				task.setDescription(
					"Writes the class the compiled module exports its handlers from"
				);
				task.getHandlerClass().set(extension.getHandlerClass());
				task.getDurableObjectClasses().set(extension.getDurableObjectClasses());
				task.getOutputDirectory().set(generatedSources);
				task.getHandlerClasspath().from(handlerClasspath);
				task.dependsOn("compileJava");
			});
	}

	private TaskProvider<GenerateCodecsTask> registerCodecs(
		Project project,
		ByteboxExtension extension,
		File generatedSources,
		SourceSet main,
		FileCollection handlerClasspath
	) {
		return project.getTasks().register("generateCodecs", GenerateCodecsTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Writes a JSON codec for every type annotated @JSONType");
			task.getOutputDirectory().set(generatedSources);
			task.getTypes().set(extension.getJSONTypes());
			// only the project's own classes are scanned, and the rest is there to resolve against
			task.getScanned().from(main.getOutput().getClassesDirs());
			task.getClasspath().from(handlerClasspath);
			task.dependsOn("compileJava");
		});
	}

	private TaskProvider<GenerateSerialCodecsTask> registerSerialCodecs(
		Project project,
		ByteboxExtension extension,
		File generatedSources,
		SourceSet main,
		FileCollection handlerClasspath
	) {
		return project
			.getTasks()
			.register("generateSerialCodecs", GenerateSerialCodecsTask.class, task -> {
				task.setGroup(GROUP);
				task.setDescription(
					"Writes a java.io serialization codec for every type annotated @SerialType"
				);
				task.getOutputDirectory().set(generatedSources);
				task.getTypes().set(extension.getSerialTypes());
				task.getScanned().from(main.getOutput().getClassesDirs());
				task.getClasspath().from(handlerClasspath);
				task.dependsOn("compileJava");
			});
	}

	private TaskProvider<GenerateNPMBindingsTask> registerNPMBindings(
		Project project,
		ByteboxExtension extension,
		File generatedBindings
	) {
		return project
			.getTasks()
			.register("generateNpmBindings", GenerateNPMBindingsTask.class, task -> {
				task.setGroup(GROUP);
				task.setDescription("Writes Java bindings for the npm packages asked for by name");
				task.getPackages().set(extension.getNPMBindingPackages());
				task.getJavaPackage().set(NPM_PACKAGE);
				task.getIntrospect().set(extension.getNPMIntrospection());
				task.getRunner().set(extension.getRunner());
				task.getOutputDirectory().set(generatedBindings);
				task.getNodeDirectory().set(nodeDirectory(project));
			});
	}

	/**
	 * Where {@code node_modules} is: this project, or the root of the build.
	 *
	 * <p>A single-module build installs beside the build file and a multi-module one usually installs
	 * once at the root, so both are tried before giving up on the project's own directory.
	 */
	private static File nodeDirectory(Project project) {
		File own = project.getProjectDir();
		if (new File(own, "node_modules").isDirectory()) return own;
		File root = project.getRootDir();
		return new File(root, "node_modules").isDirectory() ? root : own;
	}

	private TaskProvider<PackWasmTask> registerPack(
		Project project,
		ByteboxExtension extension,
		File workerDirectory
	) {
		return project.getTasks().register("packWasm", PackWasmTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription(
				"Puts the compiled module in the Worker and checks the size budget"
			);
			task.dependsOn("generateWasmGC", "copyWasmGCRuntime");
			task.getOutputDirectory().set(workerDirectory);
			task.getModuleType().set(extension.getSize().getModule());
			task.getCompression().set(extension.getSize().getCompression());
			task.getBudget().set(project.provider(() -> extension.getSize().budgetBytes()));
			Directory generated = project
				.getLayout()
				.getBuildDirectory()
				.dir("generated/teavm/wasm-gc")
				.get();
			task.getWasm().set(generated.file(project.getName() + ".wasm"));
			task.getRuntime().set(generated.file(project.getName() + ".wasm-runtime.js"));
		});
	}

	private TaskProvider<GenerateScaffoldTask> registerScaffold(
		Project project,
		ByteboxExtension extension,
		File workerDirectory,
		FileCollection handlerClasspath
	) {
		return project
			.getTasks()
			.register("generateWorkerScaffold", GenerateScaffoldTask.class, task -> {
				task.setGroup(GROUP);
				task.setDescription(
					"Writes the Wrangler configuration and the JavaScript entry point"
				);
				WranglerSpec wrangler = extension.getWrangler();
				task.getWorkerName().set(wrangler.getName());
				task.getCompatibilityDate().set(wrangler.getCompatibilityDate());
				task.getCompatibilityFlags().set(wrangler.getCompatibilityFlags());
				task.getRoutes().set(wrangler.getRoutes());
				task.getCrons().set(wrangler.getCrons());
				task.getTailConsumers().set(wrangler.getTailConsumers());
				task.getObservability().set(wrangler.getObservability());
				task.getOutputDirectory().set(workerDirectory);
				task.getByteboxVersion().set("^1.0.0");
				task.getNPMPackages().set(extension.getNPMPackages());
				task.getDurableObjects().set(
					project.provider(() ->
						DurableObjects.of(
							extension.getDurableObjectClasses().get(),
							handlerClasspath
						)
					)
				);
				// a Java Durable Object binds itself, so a project names the class once rather than
				// declaring the class and then the binding to reach it
				task.getBindings().set(
					project.provider(() -> {
						Bindings bindings = extension.getBindings();
						List<Binding> declared = bindings.getAll();
						List<String> names = new ArrayList<>();
						for (Binding binding : declared) names.add(binding.name());
						for (DurableObjects object : DurableObjects.of(
							extension.getDurableObjectClasses().get(),
							handlerClasspath
						)) {
							if (names.contains(object.bindingName())) continue;
							bindings.durableObject(object.className());
						}
						return bindings.getAll();
					})
				);
				task.getTriggers().set(
					project.provider(() -> {
						List<String> names = new ArrayList<>();
						for (Triggers trigger : Triggers.of(
							extension.getHandlerClass().get(),
							handlerClasspath
						)) {
							names.add(trigger.exportName());
						}
						return names;
					})
				);
				task.dependsOn("compileJava");
			});
	}

	private void registerSizeReport(Project project, ByteboxExtension extension) {
		project.getTasks().register("sizeReport", SizeReportTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Measures the compiled module on every compression axis");
			task.dependsOn("generateWasmGC");
			task.getWasm().set(
				project
					.getLayout()
					.getBuildDirectory()
					.dir("generated/teavm/wasm-gc")
					.get()
					.file(project.getName() + ".wasm")
			);
			task.getBudget().set(project.provider(() -> extension.getSize().budgetBytes()));
		});
	}

	/**
	 * Registers the Wrangler wrappers, so a bytebox project works the way a normal Workers project
	 * does without dropping out of Gradle.
	 *
	 * <p>Read-only commands run without ceremony. Anything that writes needs {@code --confirm}, and
	 * nothing here deletes a resource.
	 */
	private void registerWranglerTasks(
		Project project,
		ByteboxExtension extension,
		File workerDirectory
	) {
		record Wrapper(
			String name,
			String description,
			List<String> command,
			boolean account,
			boolean writes
		) {}

		List<Wrapper> wrappers = List.of(
			new Wrapper("workerDev", "Serves the Worker locally", List.of("dev"), false, false),
			new Wrapper("workerDeploy", "Deploys the Worker", List.of("deploy"), true, true),
			new Wrapper(
				"workerDryRun",
				"Builds and measures without deploying",
				List.of("deploy", "--dry-run"),
				false,
				false
			),
			new Wrapper("workerTail", "Streams the Worker's logs", List.of("tail"), true, false),
			new Wrapper(
				"workerTypes",
				"Writes TypeScript types for the bindings",
				List.of("types"),
				false,
				false
			),
			new Wrapper(
				"workerVersionsUpload",
				"Uploads a version without deploying it",
				List.of("versions", "upload"),
				true,
				true
			),
			new Wrapper("wrangler", "Runs Wrangler with --args", List.of(), false, false),
			new Wrapper("d1List", "Lists the D1 databases", List.of("d1", "list"), true, false),
			new Wrapper("d1Info", "Describes a D1 database", List.of("d1", "info"), true, false),
			new Wrapper(
				"d1Query",
				"Runs a read-only query with --args=\"<db> --command '<sql>'\"",
				List.of("d1", "execute"),
				true,
				false
			),
			new Wrapper(
				"d1Migrate",
				"Applies the D1 migrations",
				List.of("d1", "migrations", "apply"),
				true,
				true
			),
			new Wrapper(
				"d1MigrationsList",
				"Lists unapplied D1 migrations",
				List.of("d1", "migrations", "list"),
				true,
				false
			),
			new Wrapper(
				"kvList",
				"Lists the KV namespaces",
				List.of("kv", "namespace", "list"),
				true,
				false
			),
			new Wrapper(
				"kvGet",
				"Reads a KV key with --args",
				List.of("kv", "key", "get"),
				true,
				false
			),
			new Wrapper(
				"kvPut",
				"Writes a KV key with --args",
				List.of("kv", "key", "put"),
				true,
				true
			),
			new Wrapper(
				"kvBulkPut",
				"Writes many KV keys from a file with --args",
				List.of("kv", "bulk", "put"),
				true,
				true
			),
			new Wrapper(
				"r2List",
				"Lists the R2 buckets",
				List.of("r2", "bucket", "list"),
				true,
				false
			),
			new Wrapper(
				"r2Put",
				"Writes an R2 object with --args",
				List.of("r2", "object", "put"),
				true,
				true
			),
			new Wrapper(
				"aiModels",
				"Lists the Workers AI models",
				List.of("ai", "models"),
				true,
				false
			),
			new Wrapper(
				"aiFinetunes",
				"Lists the Workers AI finetunes",
				List.of("ai", "finetune", "list"),
				true,
				false
			),
			new Wrapper("queueList", "Lists the queues", List.of("queues", "list"), true, false),
			new Wrapper(
				"queueInfo",
				"Describes a queue with --args",
				List.of("queues", "info"),
				true,
				false
			),
			new Wrapper(
				"secretList",
				"Lists the secret names, never their values",
				List.of("secret", "list"),
				true,
				false
			),
			new Wrapper(
				"hyperdriveList",
				"Lists the Hyperdrive configurations",
				List.of("hyperdrive", "list"),
				true,
				false
			),
			new Wrapper(
				"vectorizeList",
				"Lists the Vectorize indexes",
				List.of("vectorize", "list"),
				true,
				false
			),
			new Wrapper(
				"workflowsList",
				"Lists the Workflows",
				List.of("workflows", "list"),
				true,
				false
			),
			new Wrapper(
				"versionsList",
				"Lists the uploaded versions",
				List.of("versions", "list"),
				true,
				false
			)
		);

		for (Wrapper wrapper : wrappers) {
			project.getTasks().register(wrapper.name(), WranglerTask.class, task -> {
				task.setGroup(GROUP);
				task.setDescription(wrapper.description());
				task.getCommand().set(wrapper.command());
				task.getProjectDirectory().set(workerDirectory);
				task.getRunner().set(extension.getRunner());
				task.getRequiresAccount().set(wrapper.account());
				task.getDestructive().set(wrapper.writes());
				if (needsBuild(wrapper.name())) task.dependsOn("buildWorker");
			});
		}

		project.getTasks().register("bindingsReport", BindingsReportTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Prints every declared binding and its resolved name");
			task.getBindings().set(project.provider(() -> extension.getBindings().getAll()));
		});
	}

	/** Only the commands that read the Worker itself need it built first. */
	private static boolean needsBuild(String name) {
		return Map.of(
			"workerDev",
			true,
			"workerDeploy",
			true,
			"workerDryRun",
			true,
			"workerTypes",
			true,
			"workerVersionsUpload",
			true
		).getOrDefault(name, false);
	}
}
