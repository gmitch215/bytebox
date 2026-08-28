package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.GradleRunner;

/** Builds throwaway projects for the plugin to run against. */
final class Fixtures {

	private Fixtures() {}

	/**
	 * Writes a project that applies the plugin.
	 *
	 * <p>The core jar is put on the compile classpath by path rather than by coordinate, because the
	 * version under test has not been published anywhere a resolver could find it.
	 *
	 * @param root where to write it
	 * @param buildScript the {@code bytebox { }} configuration and anything else
	 * @param sources source files, as path to content
	 * @throws IOException when the project cannot be written
	 */
	static void project(Path root, String buildScript, java.util.Map<String, String> sources)
		throws IOException {
		Files.createDirectories(root);
		Files.writeString(
			root.resolve("settings.gradle.kts"),
			"""
			pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
			rootProject.name = "fixture"
			"""
		);

		// Kotlin requires imports before anything else, so any the caller wrote are hoisted above
		// the plugins block rather than left where they appear in the snippet
		List<String> imports = new java.util.ArrayList<>();
		List<String> body = new java.util.ArrayList<>();
		for (String line : buildScript.lines().toList()) {
			(line.strip().startsWith("import ") ? imports : body).add(line);
		}
		buildScript = String.join("\n", body);

		Files.writeString(
			root.resolve("build.gradle.kts"),
			"""
			%s

			plugins {
				id("dev.gmitch215.bytebox")
			}

			repositories { mavenCentral() }
			java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

			// the version under test is not published anywhere a resolver could find it, so these
			// projects take the jar by path and turn off the dependency the plugin would add
			bytebox { coreDependency = false }

			dependencies {
				implementation(files(%s))
				implementation("org.teavm:teavm-jso:0.15.0")
				implementation("org.teavm:teavm-jso-apis:0.15.0")
				// a file dependency carries no transitive metadata, so what core's own published
				// metadata would bring along is named here instead
				implementation("org.threeten:threetenbp:1.7.0")
			}

			%s
			""".formatted(String.join("\n", imports), coreJars(), buildScript)
		);
		for (Map.Entry<String, String> entry : sources.entrySet()) {
			Path file = root.resolve("src/main/java").resolve(entry.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry.getValue());
		}
	}

	/** The core jar, found from the property the build passes in. */
	private static String coreJars() {
		String jars = System.getProperty("bytebox.core.jars");
		if (jars == null || jars.isBlank()) {
			throw new IllegalStateException(
				"the functionalTest task has to pass -Dbytebox.core.jars with the core jar's path"
			);
		}
		List<String> quoted = new java.util.ArrayList<>();
		for (String jar : jars.split(java.io.File.pathSeparator)) {
			quoted.add("\"" + jar.replace("\\", "\\\\") + "\"");
		}
		return String.join(", ", quoted);
	}

	/**
	 * A runner over a project.
	 *
	 * @param root the project directory
	 * @param arguments the Gradle arguments
	 * @return the runner
	 */
	static GradleRunner runner(Path root, String... arguments) {
		List<String> all = new java.util.ArrayList<>(List.of(arguments));
		all.add("--stacktrace");
		return GradleRunner.create()
			.withProjectDir(root.toFile())
			.withPluginClasspath()
			.withArguments(all)
			.forwardOutput();
	}

	/** A handler implementing only {@code Worker}. */
	static String fetchHandler() {
		return """
		package fixture;

		import dev.gmitch215.bytebox.Bytebox;
		import dev.gmitch215.bytebox.Env;
		import dev.gmitch215.bytebox.ExecutionCtx;
		import dev.gmitch215.bytebox.Request;
		import dev.gmitch215.bytebox.Response;
		import dev.gmitch215.bytebox.Worker;

		public class Handler implements Worker {
			@Override
			public Response fetch(Request request, Env env, ExecutionCtx ctx) {
				return Bytebox.response("hello from " + request.path());
			}
		}
		""";
	}

	/** A handler implementing {@code Worker} and {@code Scheduled}. */
	static String twoTriggerHandler() {
		return """
		package fixture;

		import dev.gmitch215.bytebox.Bytebox;
		import dev.gmitch215.bytebox.Cron;
		import dev.gmitch215.bytebox.Env;
		import dev.gmitch215.bytebox.ExecutionCtx;
		import dev.gmitch215.bytebox.Request;
		import dev.gmitch215.bytebox.Response;
		import dev.gmitch215.bytebox.Scheduled;
		import dev.gmitch215.bytebox.Worker;

		public class Handler implements Worker, Scheduled {
			@Override
			public Response fetch(Request request, Env env, ExecutionCtx ctx) {
				return Bytebox.response("ok");
			}

			@Override
			public void scheduled(Cron cron, Env env, ExecutionCtx ctx) {
				Bytebox.log(cron.expression());
			}
		}
		""";
	}
}
