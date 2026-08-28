package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes Java bindings for the npm packages a project asks for, from their TypeScript types.
 *
 * <p>The reading is done by {@code bytebox-bindgen}, which ships with the bytebox npm package,
 * because the thing that understands a package's types is the TypeScript compiler and it does not run
 * on the JVM. This task resolves that command, runs it, and puts what it writes on the main source
 * set so a project calls the bindings like any other class.
 *
 * <p>Declared packages are not generated for unless they are also asked for by name. Generation needs
 * the package installed and a JavaScript runtime on {@code PATH}, and neither is true of every build
 * that merely depends on a package.
 *
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "reads node_modules, which is not a declared input")
public abstract class GenerateNPMBindingsTask extends DefaultTask {

	/** Where the bytebox npm package puts the generator once it is installed. */
	static final String LOCAL_SCRIPT = "node_modules/bytebox/dist/bin/bindgen.js";

	private final ExecOperations exec;

	/**
	 * @param exec Gradle's process runner
	 */
	@Inject
	public GenerateNPMBindingsTask(ExecOperations exec) {
		this.exec = exec;
	}

	/** {@return the package specifiers to bind, each optionally naming a subpath export} */
	@Input
	public abstract ListProperty<String> getPackages();

	/** {@return the Java package the generated classes live in} */
	@Input
	public abstract Property<String> getJavaPackage();

	/** {@return whether the generator may import a package to read exports it cannot see} */
	@Input
	public abstract Property<Boolean> getIntrospect();

	/** {@return the directory holding {@code node_modules}} */
	@Internal
	public abstract DirectoryProperty getNodeDirectory();

	/** {@return the pinned runner, or empty to search} */
	@Input
	@Optional
	public abstract Property<String> getRunner();

	/** {@return where the generated source goes} */
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	/** Runs the generator. */
	@TaskAction
	public void generate() {
		List<String> packages = getPackages().get();
		if (packages.isEmpty()) return;

		File node = getNodeDirectory().get().getAsFile();
		File output = getOutputDirectory().get().getAsFile();
		List<String> command = command(node, output, packages);

		getLogger().lifecycle("bytebox: binding {}", String.join(", ", packages));
		exec.exec(spec -> {
			spec.commandLine(command);
			spec.workingDir(node);
		});
	}

	/**
	 * The full command line.
	 *
	 * <p>An installed copy of the generator is preferred over a package runner, because running the
	 * script directly cannot reach the registry and so cannot pick up a different version than the one
	 * the project depends on.
	 *
	 * @param node the directory holding {@code node_modules}
	 * @param output where the generated source goes
	 * @param packages the package specifiers
	 * @return the command
	 */
	List<String> command(File node, File output, List<String> packages) {
		List<String> command = new ArrayList<>(prefix(node));
		command.add("--out");
		command.add(output.getAbsolutePath());
		command.add("--root");
		command.add(node.getAbsolutePath());
		command.add("--java-package");
		command.add(getJavaPackage().get());
		if (!getIntrospect().getOrElse(true)) command.add("--no-introspect");
		command.addAll(packages);
		return command;
	}

	/** Whatever runs the generator: the installed script if there is one, else a package runner. */
	private List<String> prefix(File node) {
		File script = new File(node, LOCAL_SCRIPT);
		if (script.isFile()) {
			for (String runtime : List.of("node", "bun", "deno")) {
				if (Runner.which(runtime) == null) continue;
				return runtime.equals("deno")
					? List.of(runtime, "run", "-A", script.getAbsolutePath())
					: List.of(runtime, script.getAbsolutePath());
			}
		}
		return Runner.resolve(getRunner().getOrNull(), Runner.BINDGEN).command(List.of());
	}
}
