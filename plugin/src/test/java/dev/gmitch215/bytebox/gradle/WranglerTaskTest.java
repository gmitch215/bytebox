package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("running Wrangler")
class WranglerTaskTest {

	/** Present on any machine this build runs on, which is what makes the lookup checkable. */
	private static final String SHELL = System.getProperty("os.name", "")
		.toLowerCase(Locale.ROOT)
		.contains("win")
		? "cmd"
		: "sh";

	@TempDir
	Path directory;

	@Test
	@DisplayName("refuses a command that changes something without --confirm")
	void needsConfirming() {
		WranglerTask task = task("deploy");
		task.getDestructive().set(true);

		GradleException failure = assertThrows(GradleException.class, task::run);

		assertTrue(failure.getMessage().contains("--confirm"), failure.getMessage());
		assertTrue(failure.getMessage().contains("Nothing has been done"), failure.getMessage());
	}

	@Test
	@DisplayName("says to build the Worker first when there is nothing to run in")
	void needsTheWorker() {
		WranglerTask task = task("dev");
		task.getConfirmed().set(true);
		task.getProjectDirectory().set(directory.resolve("absent").toFile());

		GradleException failure = assertThrows(GradleException.class, task::run);

		assertTrue(failure.getMessage().contains("buildWorker first"), failure.getMessage());
	}

	@Test
	@DisplayName("takes extra arguments and a confirmation from the command line")
	void options() {
		WranglerTask task = task("dev");
		task.setArgsOption("--port 8080");
		task.setConfirmedOption(true);

		assertEquals("--port 8080", task.getArgs().get());
		assertTrue(task.getConfirmed().get());
	}

	@Test
	@DisplayName("finds a tool already on PATH and runs it directly")
	void resolvesAnInstalledTool() {
		Runner runner = Runner.resolve(null, SHELL);

		assertEquals(SHELL, runner.executable());
		assertEquals(SHELL, runner.describe());
		assertEquals(List.of(SHELL, "--version"), runner.command(List.of("--version")));
	}

	@Test
	@DisplayName("takes a pinned runner over the search")
	void honoursAPinnedRunner() {
		assertEquals(SHELL, Runner.resolve(SHELL, SHELL).executable());
	}

	@Test
	@DisplayName("says so when the pinned runner is not there")
	void refusesAMissingRunner() {
		GradleException failure = assertThrows(GradleException.class, () ->
			Runner.resolve("nothing-by-this-name", "wrangler")
		);

		assertTrue(failure.getMessage().contains("bytebox.runner is set to"), failure.getMessage());
		assertTrue(failure.getMessage().contains("not on PATH"), failure.getMessage());
	}

	@Test
	@DisplayName("puts the tool's name after the runner that has to launch it")
	void launchesAnUninstalledTool() {
		Runner runner = Runner.resolve(SHELL, "bytebox-bindgen");

		assertEquals(SHELL + " bytebox-bindgen", runner.describe());
		assertEquals(
			List.of(SHELL, "bytebox-bindgen", "nanoid"),
			runner.command(List.of("nanoid"))
		);
	}

	@Test
	@DisplayName("looks up an executable on PATH without spawning a process to ask")
	void looksUpOnPath() {
		assertNotNull(Runner.which(SHELL));
		assertNull(Runner.which("nothing-by-this-name"));
	}

	private WranglerTask task(String command) {
		WranglerTask task = Projects.task(Projects.project(directory), WranglerTask.class);
		task.getCommand().set(List.of(command));
		try {
			Files.createDirectories(directory.resolve("worker"));
		} catch (java.io.IOException unreachable) {
			throw new java.io.UncheckedIOException(unreachable);
		}
		task.getProjectDirectory().set(directory.resolve("worker").toFile());
		return task;
	}
}
