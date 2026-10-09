package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.logging.StandardOutputListener;
import org.teavm.gradle.tasks.TeaVMTask;

/**
 * Fails a TeaVM compile whose {@code @JSBody} script did not parse.
 *
 * <p>The compiler reports a script it cannot parse as a warning and carries on, so the build ends
 * successful with a module that fails when it loads. The warning is the only trace, so this reads
 * the task's own output for it. The compiler's parser is older than the engine that runs the module,
 * so a script it reports is parsed again by {@code node}'s V8, and only one V8 refuses too fails the
 * build.
 *
 * @since 1.0.3
 */
final class JSBodyErrors {

	/** What the compiler's warning starts with. */
	static final String MARKER = "Error in @JSBody script";

	// not Task.extensions, which the configuration cache refuses at execution time
	private static final Map<Task, Collector> LISTENING = Collections.synchronizedMap(
		new WeakHashMap<>()
	);

	private static final String JSBODY = "org.teavm.jso.JSBody";

	// the method line the compiler prints under an error: class, then method
	private static final Pattern LOCATION = Pattern.compile("\\n\\s*at (\\S+)\\.([^.\\s]+)\\s*$");

	// the platform loader as parent, so a project's classes never meet the plugin's own
	private static final ClassLoader PLATFORM = ClassLoader.getPlatformClassLoader();

	// stdin is params and script pairs split by NUL; stdout is V8's message for each ("" when it parses)
	private static final String PARSER = """
	const parts = require('node:fs').readFileSync(0, 'utf8').split('\\0');
	const verdicts = [];
	for (let i = 0; i + 1 < parts.length; i += 2) {
		try {
			new Function(...(parts[i] ? parts[i].split(',') : []), parts[i + 1]);
			verdicts.push('');
		} catch (error) {
			verdicts.push(String(error.message).replaceAll('\\0', ' ') || 'refused');
		}
	}
	process.stdout.write(verdicts.join('\\0'));
	""";

	private JSBodyErrors() {}

	/**
	 * Makes a compile task fail when it printed a script error.
	 *
	 * @param task the compile task
	 */
	static void watch(Task task) {
		task.doFirst("listenForJSBodyErrors", JSBodyErrors::listen);
		task.doLast("failOnJSBodyErrors", JSBodyErrors::check);
	}

	private static void listen(Task task) {
		Collector collector = new Collector();
		LISTENING.put(task, collector);
		task.getLogging().addStandardOutputListener(collector);
		task.getLogging().addStandardErrorListener(collector);
	}

	private static void check(Task task) {
		Collector collector = LISTENING.remove(task);
		task.getLogging().removeStandardOutputListener(collector);
		task.getLogging().removeStandardErrorListener(collector);
		List<String> errors = refusedByV8(
			collector.errors(),
			task instanceof TeaVMTask teavm ? teavm.getClasspath().getFiles() : Set.of(),
			Runner.which("node")
		);
		if (errors.isEmpty()) return;
		throw new GradleException(
			"the compiler reported " +
				errors.size() +
				" error(s) in @JSBody scripts:\n" +
				String.join("\n", errors) +
				"\nA script with a syntax error stops the whole module from loading. One V8 reads but the" +
				" compiler's parser does not, such as for...of, passes only when node is on PATH."
		);
	}

	/**
	 * Keeps the errors whose script V8 refuses too.
	 *
	 * <p>The compiler's parser reads an older JavaScript than the engine that runs the module, so it
	 * reports {@code for...of} and a BigInt literal, which load and run. Each reported script is read
	 * back from its class and parsed by {@code node} as a function body, which is how the runtime
	 * builds it. An error that cannot be traced to a script, or checked without {@code node}, is kept.
	 *
	 * @param errors what {@link Collector#errors()} found
	 * @param classpath where the compiled classes are
	 * @param node the {@code node} executable, or {@code null} when there is none
	 * @return the errors that stand, each with V8's own message when it was asked
	 */
	static List<String> refusedByV8(List<String> errors, Collection<File> classpath, File node) {
		if (node == null) return errors;
		List<List<Script>> scripts = new ArrayList<>();
		try (URLClassLoader loader = new URLClassLoader(urls(classpath), PLATFORM)) {
			for (String error : errors) scripts.add(scriptsOf(loader, error));
		} catch (IOException | RuntimeException failure) {
			return errors;
		}
		List<Script> all = scripts.stream().flatMap(List::stream).toList();
		List<String> verdicts = all.isEmpty() ? List.of() : parse(node, all);
		if (verdicts == null) return errors;

		List<String> refused = new ArrayList<>();
		int next = 0;
		for (int i = 0; i < errors.size(); i++) {
			int count = scripts.get(i).size();
			if (count == 0) {
				refused.add(errors.get(i));
				continue;
			}
			String verdict = verdicts
				.subList(next, next + count)
				.stream()
				.filter(message -> !message.isEmpty())
				.findFirst()
				.orElse(null);
			next += count;
			if (verdict != null) refused.add(errors.get(i) + "\n    V8: " + verdict);
		}
		return refused;
	}

	private static URL[] urls(Collection<File> classpath) {
		List<URL> urls = new ArrayList<>();
		for (File file : classpath) {
			try {
				urls.add(file.toURI().toURL());
			} catch (MalformedURLException skipped) {
				continue;
			}
		}
		return urls.toArray(URL[]::new);
	}

	/** {@return the scripts of the method an error names, or none if it names no method found} */
	private static List<Script> scriptsOf(ClassLoader loader, String error) {
		Matcher at = LOCATION.matcher(error);
		if (!at.find()) return List.of();
		List<Script> scripts = new ArrayList<>();
		try {
			for (Method method : Class.forName(at.group(1), false, loader).getDeclaredMethods()) {
				if (!method.getName().equals(at.group(2))) continue;
				for (Annotation annotation : method.getDeclaredAnnotations()) {
					Class<? extends Annotation> type = annotation.annotationType();
					if (!type.getName().equals(JSBODY)) continue;
					String[] params = (String[]) type.getMethod("params").invoke(annotation);
					String script = (String) type.getMethod("script").invoke(annotation);
					scripts.add(new Script(String.join(",", params), script));
				}
			}
		} catch (ReflectiveOperationException | LinkageError unreadable) {
			return List.of();
		}
		return scripts;
	}

	/** {@return V8's message for each script, empty for one it reads, or {@code null} if node failed} */
	private static List<String> parse(File node, List<Script> scripts) {
		StringBuilder input = new StringBuilder();
		for (Script script : scripts) {
			input.append(script.params()).append('\0').append(script.body()).append('\0');
		}
		try {
			Process process = new ProcessBuilder(node.getPath(), "-e", PARSER)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			try (OutputStream stdin = process.getOutputStream()) {
				stdin.write(input.toString().getBytes(StandardCharsets.UTF_8));
			}
			String output = new String(
				process.getInputStream().readAllBytes(),
				StandardCharsets.UTF_8
			);
			if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
			List<String> verdicts = List.of(output.split("\0", -1));
			return verdicts.size() == scripts.size() ? verdicts : null;
		} catch (IOException unrunnable) {
			return null;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/** One script and the parameter names it was declared with, comma-joined. */
	private record Script(String params, String body) {}

	/** Collects what a task printed, which arrives in pieces that need not end on a line. */
	static final class Collector implements StandardOutputListener {

		private final StringBuilder seen = new StringBuilder();

		@Override
		public void onOutput(CharSequence output) {
			seen.append(output);
		}

		/** {@return each error line with the method the compiler named under it} */
		List<String> errors() {
			List<String> lines = seen.toString().lines().toList();
			List<String> errors = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				if (!lines.get(i).startsWith(MARKER)) continue;
				boolean located =
					i + 1 < lines.size() &&
					lines
						.get(i + 1)
						.strip()
						.startsWith("at ");
				errors.add(located ? lines.get(i) + "\n" + lines.get(i + 1) : lines.get(i));
			}
			return errors;
		}
	}
}
