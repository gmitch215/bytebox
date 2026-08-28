package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.gradle.api.GradleException;

/**
 * Finds something that can run a Node command-line tool.
 *
 * <p>Resolved once, in the order a developer would try by hand: the tool itself if it is already
 * installed, then {@code bunx}, then {@code deno}, then {@code npx}. An installed binary comes first
 * because it is the fastest and because a project that installed one meant to use it.
 *
 * @since 1.0.0
 */
public final class Runner {

	/** The Wrangler CLI, which is what most of these tasks run. */
	public static final String WRANGLER = "wrangler";

	/** The binding generator, which ships with the bytebox npm package. */
	public static final String BINDGEN = "bytebox-bindgen";

	private final String executable;
	private final List<String> prefix;

	private Runner(String executable, List<String> prefix) {
		this.executable = executable;
		this.prefix = List.copyOf(prefix);
	}

	/**
	 * Resolves a runner for the Wrangler CLI.
	 *
	 * @param override a pinned runner name, or {@code null} to search
	 * @return the runner
	 */
	public static Runner resolve(String override) {
		return resolve(override, WRANGLER);
	}

	/**
	 * Resolves a runner for a named tool, or explains what to install.
	 *
	 * @param override a pinned runner name, or {@code null} to search
	 * @param tool the npm command to run
	 * @return the runner
	 */
	public static Runner resolve(String override, String tool) {
		if (override != null && !override.isBlank()) {
			Runner pinned = candidate(override, tool);
			if (pinned != null) return pinned;
			throw new GradleException(
				"bytebox.runner is set to " + override + ", which is not on PATH"
			);
		}
		for (String name : List.of(tool, "bunx", "deno", "npx")) {
			Runner found = candidate(name, tool);
			if (found != null) return found;
		}
		throw new GradleException(
			"found nothing to run " +
				tool +
				" with. Install one of bun, deno or node, or put " +
				tool +
				" on PATH, or set bytebox.runner."
		);
	}

	private static Runner candidate(String name, String tool) {
		if (which(name) == null) return null;
		if (name.equals(tool)) return new Runner(name, List.of());
		return switch (name) {
			case "deno" -> new Runner(name, List.of("run", "-A", "npm:" + tool));
			default -> new Runner(name, List.of(tool));
		};
	}

	/** {@return the resolved executable, so a build can log which one it found} */
	public String executable() {
		return executable;
	}

	/** {@return a description for the build log} */
	public String describe() {
		return prefix.isEmpty() ? executable : executable + " " + String.join(" ", prefix);
	}

	/**
	 * The full command line for a Wrangler invocation.
	 *
	 * @param args the Wrangler arguments
	 * @return the command
	 */
	public List<String> command(List<String> args) {
		List<String> command = new ArrayList<>();
		command.add(executable);
		command.addAll(prefix);
		command.addAll(args);
		return command;
	}

	/**
	 * Looks for an executable on {@code PATH}.
	 *
	 * <p>Done by hand rather than by running {@code which}, because spawning a process to ask whether
	 * a process can be spawned is slower and behaves differently on Windows.
	 *
	 * @param name the executable
	 * @return where it is, or {@code null}
	 */
	static File which(String name) {
		String path = System.getenv("PATH");
		if (path == null) return null;
		boolean windows = System.getProperty("os.name", "")
			.toLowerCase(Locale.ROOT)
			.contains("win");
		List<String> names = windows
			? List.of(name + ".exe", name + ".cmd", name + ".bat", name)
			: List.of(name);
		for (String directory : path.split(File.pathSeparator)) {
			for (String candidate : names) {
				File file = new File(directory, candidate);
				if (file.isFile() && (windows || file.canExecute())) return file;
			}
		}
		return null;
	}
}
