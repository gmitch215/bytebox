package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command line the plugin hands the generator.
 *
 * <p>What the generator does with a package is the npm package's own test lane, because the thing
 * that reads TypeScript is the TypeScript compiler. What is tested here is the half that lives on the
 * JVM: which arguments go out, and that a project asking for nothing runs nothing.
 */
class NPMBindingsTest {

	@TempDir
	Path directory;

	private GenerateNPMBindingsTask task() {
		Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
		GenerateNPMBindingsTask task = project
			.getTasks()
			.register("generateNpmBindings", GenerateNPMBindingsTask.class)
			.get();
		task.getJavaPackage().set(ByteboxPlugin.NPM_PACKAGE);
		task.getIntrospect().set(true);
		return task;
	}

	@Test
	void passesEveryPackageAndBothDirectories() {
		GenerateNPMBindingsTask task = task();
		File node = directory.resolve("project").toFile();
		File out = directory.resolve("generated").toFile();

		List<String> command = task.command(node, out, List.of("nanoid", "@noble/hashes"));

		assertTrue(command.contains("--out"));
		assertEquals(out.getAbsolutePath(), command.get(command.indexOf("--out") + 1));
		assertEquals(node.getAbsolutePath(), command.get(command.indexOf("--root") + 1));
		assertEquals(ByteboxPlugin.NPM_PACKAGE, command.get(command.indexOf("--java-package") + 1));
		assertEquals("@noble/hashes", command.get(command.size() - 1));
		assertEquals("nanoid", command.get(command.size() - 2));
	}

	@Test
	void refusesIntrospectionWhenTheProjectDoes() {
		GenerateNPMBindingsTask task = task();
		task.getIntrospect().set(false);

		List<String> command = task.command(directory.toFile(), directory.toFile(), List.of("qs"));

		assertTrue(command.contains("--no-introspect"));
	}

	@Test
	void leavesIntrospectionOnByDefault() {
		List<String> command = task().command(
			directory.toFile(),
			directory.toFile(),
			List.of("qs")
		);

		assertFalse(command.contains("--no-introspect"));
	}

	@Test
	void prefersAnInstalledGeneratorOverAPackageRunner() throws IOException {
		GenerateNPMBindingsTask task = task();
		File node = directory.resolve("installed").toFile();
		Path script = node.toPath().resolve(GenerateNPMBindingsTask.LOCAL_SCRIPT);
		Files.createDirectories(script.getParent());
		Files.writeString(script, "// the generator\n");

		List<String> command = task.command(node, directory.toFile(), List.of("nanoid"));

		assertTrue(
			command.get(0).equals("node") ||
				command.get(0).equals("bun") ||
				command.get(0).equals("deno"),
			"expected a JavaScript runtime, got " + command.get(0)
		);
		assertTrue(
			command.contains(script.toFile().getAbsolutePath()),
			"expected the installed script on the command line"
		);
	}

	@Test
	void doesNothingWhenNoPackageWasAskedFor() {
		GenerateNPMBindingsTask task = task();
		task.getPackages().set(List.of());
		task.getNodeDirectory().set(directory.resolve("absent").toFile());
		task.getOutputDirectory().set(directory.resolve("generated").toFile());

		// no runtime is resolved and no process starts, which is what keeps a build that declares no
		// packages from needing node at all
		task.generate();

		assertFalse(directory.resolve("generated/dev").toFile().exists());
	}
}
