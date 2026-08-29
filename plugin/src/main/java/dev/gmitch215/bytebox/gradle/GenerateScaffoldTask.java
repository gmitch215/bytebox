package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes the Worker project around the compiled module: the Wrangler configuration, the JavaScript
 * entry point, and the package manifest.
 *
 * <p>Everything it writes is derived, so it is safe to delete and regenerate. What it derives from is
 * the handler's own interfaces and the declared bindings, which is what keeps the configuration from
 * drifting away from the code.
 *
 * @since 1.0.0
 */
@CacheableTask
public abstract class GenerateScaffoldTask extends DefaultTask {

	/** The npm package the generated Worker imports the loader from. */
	private static final String PACKAGE = "@gmitch215/bytebox";

	/** {@return the Worker's name} */
	@Input
	public abstract Property<String> getWorkerName();

	/** {@return the compatibility date} */
	@Input
	public abstract Property<String> getCompatibilityDate();

	/** {@return the compatibility flags, before the Node ones are dealt with} */
	@Input
	public abstract ListProperty<String> getCompatibilityFlags();

	/** {@return the routes the Worker answers on} */
	@Input
	public abstract ListProperty<String> getRoutes();

	/** {@return the Cron Triggers that fire it} */
	@Input
	public abstract ListProperty<String> getCrons();

	/** {@return the Workers this one sends logs to} */
	@Input
	public abstract ListProperty<String> getTailConsumers();

	/** {@return whether to send logs to Cloudflare's observability} */
	@Input
	public abstract Property<Boolean> getObservability();

	/** {@return the triggers the handler handles, by export name} */
	@Input
	public abstract ListProperty<String> getTriggers();

	/** {@return the declared bindings} */
	@Input
	public abstract ListProperty<Binding> getBindings();

	/** {@return the npm package supplying the synchronous decompressor, when one is needed} */
	@Input
	@Optional
	public abstract Property<String> getDecoder();

	/** {@return the version range for that package} */
	@Input
	@Optional
	public abstract Property<String> getDecoderVersion();

	/** {@return the version of bytebox to depend on} */
	@Input
	public abstract Property<String> getByteboxVersion();

	/** {@return the npm packages the program imports, as {@code name@range}} */
	@Input
	public abstract ListProperty<String> getNPMPackages();

	/** {@return the Durable Objects written in Java, each needing a JavaScript class to be one} */
	@Input
	public abstract ListProperty<DurableObjects> getDurableObjects();

	/** {@return where the project is written} */
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	/** Writes the project. */
	@TaskAction
	public void generate() {
		LocalDate date = CompatibilityDate.parse(getCompatibilityDate().get());
		List<String> flags = new ArrayList<>(getCompatibilityFlags().get());

		Map<String, LocalDate> redundant = CompatibilityDate.redundant(date, flags);
		redundant.forEach((flag, since) ->
			getLogger().warn(
				"bytebox: the compatibility flag {} has been the default since {}, so declaring it" +
					" for {} does nothing",
				flag,
				since,
				date
			)
		);

		// Java needs no Node polyfills, and the compiler's runtime reads a `process` global as proof
		// it is running under Node, which sends it down a filesystem path this platform has none of
		if (CompatibilityDate.nodeCompatByDefault(date)) {
			addUnlessPresent(flags, "no_nodejs_compat");
			addUnlessPresent(flags, "no_nodejs_compat_v2");
		}

		Path root = getOutputDirectory().get().getAsFile().toPath();
		try {
			Files.createDirectories(root.resolve("src"));
			Files.writeString(root.resolve("wrangler.jsonc"), wrangler(date, flags));
			Files.writeString(root.resolve("src/index.ts"), index());
			Files.writeString(root.resolve("package.json"), manifest());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		getLogger().lifecycle("bytebox: wrote the Worker project to {}", root);
	}

	private static void addUnlessPresent(List<String> flags, String flag) {
		String opposite = flag.startsWith("no_") ? flag.substring(3) : "no_" + flag;
		if (!flags.contains(flag) && !flags.contains(opposite)) flags.add(flag);
	}

	private String wrangler(LocalDate date, List<String> flags) {
		JSON out = new JSON();
		out.open();
		out.string("name", getWorkerName().get());
		out.string("main", "src/index.ts");
		out.string("compatibility_date", date.toString());
		out.strings("compatibility_flags", flags);
		out.raw(
			"rules",
			"[{ \"type\": \"Data\", \"globs\": [\"**/*.wasmbin\"], \"fallthrough\": false }]"
		);
		if (getObservability().getOrElse(true)) {
			out.raw("observability", "{ \"enabled\": true }");
		}
		if (!getRoutes().get().isEmpty()) out.strings("routes", getRoutes().get());
		if (!getCrons().get().isEmpty()) {
			out.raw("triggers", "{ \"crons\": " + JSON.array(getCrons().get()) + " }");
		}
		if (!getTailConsumers().get().isEmpty()) {
			List<String> consumers = new ArrayList<>();
			for (String worker : getTailConsumers().get()) {
				consumers.add("{ \"service\": " + JSON.quote(worker) + " }");
			}
			out.raw("tail_consumers", "[" + String.join(", ", consumers) + "]");
		}
		bindings(out);
		migrations(out);
		out.close();
		return out.toString();
	}

	/**
	 * The migration that creates each Java Durable Object's class.
	 *
	 * <p>A Durable Object class has to be declared to the platform before it can be addressed, and the
	 * declaration is a migration rather than a binding. New classes get SQLite-backed storage, which is
	 * the current default and what the storage API here is written against.
	 */
	private void migrations(JSON out) {
		List<DurableObjects> objects = getDurableObjects().get();
		if (objects.isEmpty()) return;
		List<String> classes = new ArrayList<>();
		for (DurableObjects object : objects) classes.add(object.simpleName());
		out.raw(
			"migrations",
			"[{ \"tag\": \"v1\", \"new_sqlite_classes\": " + JSON.array(classes) + " }]"
		);
	}

	/**
	 * Groups the bindings by the Wrangler key they live under.
	 *
	 * <p>Most keys hold an array. The account-level services hold a bare object or a boolean, which
	 * is why they are written apart rather than through one path with a special case.
	 */
	private void bindings(JSON out) {
		Map<BindingType, List<Binding>> grouped = new EnumMap<>(BindingType.class);
		for (Binding binding : getBindings().get()) {
			grouped.computeIfAbsent(binding.type(), unused -> new ArrayList<>()).add(binding);
		}

		grouped.forEach((type, declared) -> {
			switch (type) {
				case AI, IMAGES, BROWSER, VERSION_METADATA -> out.raw(
					type.configKey(),
					"{ \"binding\": " + JSON.quote(declared.get(0).name()) + " }"
				);
				case ASSETS -> out.raw(type.configKey(), JSON.object(declared.get(0).toConfig()));
				case DURABLE_OBJECT -> {
					List<String> entries = new ArrayList<>();
					for (Binding binding : declared) entries.add(JSON.object(binding.toConfig()));
					out.raw(
						type.configKey(),
						"{ \"bindings\": [" + String.join(", ", entries) + "] }"
					);
				}
				default -> {
					List<String> entries = new ArrayList<>();
					for (Binding binding : declared) entries.add(JSON.object(binding.toConfig()));
					out.raw(type.configKey(), "[" + String.join(", ", entries) + "]");
				}
			}
		});
	}

	private String index() {
		List<String> triggers = getTriggers().get();
		List<String> packages = new ArrayList<>();
		for (String declared : getNPMPackages().get()) {
			packages.add(declared.substring(0, declared.lastIndexOf('@')));
		}

		StringBuilder out = new StringBuilder();
		out.append("import { createGate, load } from '" + PACKAGE + "';\n");
		if (!getDurableObjects().get().isEmpty()) {
			out.append("import { DurableObject } from 'cloudflare:workers';\n");
		}
		out.append("import bytes from './app.wasmbin';\n");
		out.append("import * as runtime from './app.wasm-runtime.js';\n");
		for (String name : packages) {
			out.append("import * as ")
				.append(identifier(name))
				.append(" from '")
				.append(name)
				.append("';\n");
		}
		out.append('\n');
		if (packages.isEmpty()) {
			out.append(
				"""
				// module scope, because workerd allows WebAssembly compilation only during module
				// evaluation. Everything the compiled program needs is resolved here too.
				const java = load({ runtime, bytes });
				"""
			);
		} else {
			// a package the program imports is named in a wasm custom section, which no bundler can
			// follow. The static imports above are what make it resolvable.
			List<String> entries = new ArrayList<>();
			for (String name : packages) {
				entries.add(JSON.quote(name) + ": " + identifier(name));
			}
			out.append(
				"""
				// module scope, because workerd allows WebAssembly compilation only during module
				// evaluation. Everything the compiled program needs is resolved here too.
				const java = load({
				\truntime,
				\tbytes,
				\tmodules: { %s }
				});
				""".formatted(String.join(", ", entries))
			);
		}
		out.append(
			"""

			java.call('main', []);

			// one wasm heap serves every request the isolate takes, and a handler that suspends has
			// yielded to the event loop. Without this, a second request settles the first one's
			// promise from its own context and the runtime cancels it.
			const gate = createGate();

			"""
		);
		out.append("export default {\n");
		for (int i = 0; i < triggers.size(); i++) {
			out.append(handler(triggers.get(i)));
			out.append(i == triggers.size() - 1 ? "\n" : ",\n");
		}
		out.append("};\n");

		for (DurableObjects object : getDurableObjects().get()) {
			out.append('\n').append(durableClass(object));
		}
		return out.toString();
	}

	/**
	 * The JavaScript class the runtime instantiates for a Durable Object.
	 *
	 * <p>A Durable Object is a class the runtime constructs and calls methods on, and a compiled Java
	 * program exports functions rather than classes. So the class is here, forwarding into the exports,
	 * and the instance's own identifier is what pairs it with a Java object.
	 *
	 * <p>Every method goes through the same gate as a request. One heap serves the whole isolate, and a
	 * Durable Object method arriving while a request is parked is the same reentrancy.
	 */
	private String durableClass(DurableObjects object) {
		String name = object.simpleName();
		String prefix = object.exportPrefix();
		StringBuilder out = new StringBuilder();

		out.append("export class ").append(name).append(" extends DurableObject {\n");
		out.append("\tconstructor(ctx: DurableObjectState, env: unknown) {\n");
		out.append("\t\tsuper(ctx, env);\n");
		out.append("\t\tthis.id = ctx.id.toString();\n");
		out.append("\t}\n\n");

		out.append("\tfetch(request: Request): Promise<Response> {\n");
		out.append("\t\treturn gate.run(() =>\n");
		out.append("\t\t\tjava.call('")
			.append(prefix)
			.append("Fetch', this.id, request, this.ctx, this.env)\n");
		out.append("\t\t) as Promise<Response>;\n");
		out.append("\t}\n");

		if (object.alarms()) {
			out.append("\n\talarm(): Promise<void> {\n");
			out.append("\t\treturn gate.run(() =>\n");
			out.append("\t\t\tjava.call('")
				.append(prefix)
				.append("Alarm', this.id, this.ctx, this.env)\n");
			out.append("\t\t) as Promise<void>;\n");
			out.append("\t}\n");
		}

		if (object.sockets()) {
			out.append(
				"\n\twebSocketMessage(socket: WebSocket, message: string | ArrayBuffer) {\n"
			);
			out.append(
				"\t\t// text and bytes take different exports, because the Java signatures differ\n"
			);
			out.append("\t\tconst name =\n");
			out.append("\t\t\ttypeof message === 'string'\n");
			out.append("\t\t\t\t? '").append(prefix).append("MessageText'\n");
			out.append("\t\t\t\t: '").append(prefix).append("MessageBytes';\n");
			out.append("\t\treturn gate.run(() =>\n");
			out.append("\t\t\tjava.call(name, this.id, socket, message, this.ctx, this.env)\n");
			out.append("\t\t) as Promise<void>;\n");
			out.append("\t}\n");

			out.append(
				"\n\twebSocketClose(socket: WebSocket, code: number, reason: string, clean: boolean) {\n"
			);
			out.append("\t\treturn gate.run(() =>\n");
			out.append("\t\t\tjava.call(\n");
			out.append("\t\t\t\t'").append(prefix).append("Closed',\n");
			out.append("\t\t\t\tthis.id,\n\t\t\t\tsocket,\n\t\t\t\tcode,\n\t\t\t\treason,\n");
			out.append("\t\t\t\tclean,\n\t\t\t\tthis.ctx,\n\t\t\t\tthis.env\n");
			out.append("\t\t\t)\n");
			out.append("\t\t) as Promise<void>;\n");
			out.append("\t}\n");

			out.append("\n\twebSocketError(socket: WebSocket, error: unknown) {\n");
			out.append("\t\treturn gate.run(() =>\n");
			out.append("\t\t\tjava.call(\n");
			out.append("\t\t\t\t'").append(prefix).append("Failed',\n");
			out.append("\t\t\t\tthis.id,\n\t\t\t\tsocket,\n\t\t\t\tString(error),\n");
			out.append("\t\t\t\tthis.ctx,\n\t\t\t\tthis.env\n");
			out.append("\t\t\t)\n");
			out.append("\t\t) as Promise<void>;\n");
			out.append("\t}\n");
		}

		out.append("}\n");
		return out.toString();
	}

	/**
	 * A JavaScript identifier for a package name.
	 *
	 * <p>A scoped name like {@code @noble/hashes} is not one, and neither is anything with a hyphen,
	 * so the parts that cannot appear in an identifier become underscores.
	 *
	 * @param packageName the npm package name
	 * @return an identifier the generated import can bind to
	 */
	private static String identifier(String packageName) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < packageName.length(); i++) {
			char c = packageName.charAt(i);
			boolean usable = Character.isLetterOrDigit(c) || c == '_' || c == '$';
			out.append(usable ? c : '_');
		}
		// an identifier cannot begin with a digit, and a scoped name begins with an underscore
		// already, so only the digit case needs a prefix
		if (out.length() > 0 && Character.isDigit(out.charAt(0))) out.insert(0, '_');
		return out.toString();
	}

	private String handler(String trigger) {
		String signature = switch (trigger) {
			case "fetch" -> "request: Request, env: unknown, ctx: ExecutionContext";
			case "scheduled" -> "controller: ScheduledController, env: unknown, ctx: ExecutionContext";
			case "email" -> "message: ForwardableEmailMessage, env: unknown, ctx: ExecutionContext";
			case "queue" -> "batch: MessageBatch, env: unknown, ctx: ExecutionContext";
			case "tail" -> "events: TraceItem[], env: unknown, ctx: ExecutionContext";
			default -> "env: unknown, ctx: ExecutionContext";
		};
		String args = trigger.equals("alarm") ? "env, ctx" : first(trigger) + ", env, ctx";
		String returns = trigger.equals("fetch") ? "Promise<Response>" : "Promise<void>";
		return """
		\tasync %s(%s): %s {
		\t\treturn gate.run(async () => {
		\t\t\tconst answer = java.call('%s', %s) as %s;
		\t\t\tjava.drain();
		\t\t\treturn await answer;
		\t\t});
		\t}""".formatted(trigger, signature, returns, trigger, args, returns);
	}

	private static String first(String trigger) {
		return switch (trigger) {
			case "fetch" -> "request";
			case "scheduled" -> "controller";
			case "email" -> "message";
			case "queue" -> "batch";
			default -> "events";
		};
	}

	private String manifest() {
		JSON out = new JSON();
		out.open();
		out.string("name", getWorkerName().get());
		out.string("private", "true");
		out.string("type", "module");
		List<String> dependencies = new ArrayList<>();
		dependencies.add(
			"\t\t" + JSON.quote(PACKAGE) + ": " + JSON.quote(getByteboxVersion().get())
		);
		if (getDecoder().isPresent()) {
			dependencies.add(
				"\t\t" +
					JSON.quote(getDecoder().get()) +
					": " +
					JSON.quote(getDecoderVersion().get())
			);
		}
		for (String declared : getNPMPackages().get()) {
			// a scoped name carries an @ of its own, so the version is whatever follows the last one
			int split = declared.lastIndexOf('@');
			dependencies.add(
				"\t\t" +
					JSON.quote(declared.substring(0, split)) +
					": " +
					JSON.quote(declared.substring(split + 1))
			);
		}
		out.raw("dependencies", "{\n" + String.join(",\n", dependencies) + "\n\t}");
		out.raw(
			"devDependencies",
			"{\n\t\t\"wrangler\": \"^4.20.0\",\n\t\t\"typescript\": \"^5.9.3\"\n\t}"
		);
		out.close();
		return out.toString();
	}

	/**
	 * Writes JSON by hand.
	 *
	 * <p>A dependency for this would be one more thing to keep current for output nobody parses
	 * except Wrangler, and the shapes here are fixed rather than arbitrary.
	 */
	private static final class JSON {

		private final StringBuilder out = new StringBuilder();
		private boolean first = true;

		void open() {
			out.append("{\n");
		}

		void close() {
			out.append("\n}\n");
		}

		void string(String key, String value) {
			entry(key, quote(value));
		}

		void strings(String key, List<String> values) {
			entry(key, array(values));
		}

		void raw(String key, String value) {
			entry(key, value);
		}

		private void entry(String key, String value) {
			if (!first) out.append(",\n");
			first = false;
			out.append('\t').append(quote(key)).append(": ").append(value);
		}

		static String array(List<String> values) {
			List<String> quoted = new ArrayList<>();
			for (String value : values) quoted.add(quote(value));
			return "[" + String.join(", ", quoted) + "]";
		}

		static String object(Map<String, String> entries) {
			List<String> pairs = new ArrayList<>();
			entries.forEach((key, value) -> pairs.add(quote(key) + ": " + literal(value)));
			return "{ " + String.join(", ", pairs) + " }";
		}

		/** A value that is already JSON stays as it is, so a nested object survives. */
		private static String literal(String value) {
			String trimmed = value.trim();
			boolean structured = trimmed.startsWith("{") || trimmed.startsWith("[");
			return structured ? trimmed : quote(value);
		}

		static String quote(String value) {
			StringBuilder quoted = new StringBuilder("\"");
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				switch (c) {
					case '"' -> quoted.append("\\\"");
					case '\\' -> quoted.append("\\\\");
					case '\n' -> quoted.append("\\n");
					case '\r' -> quoted.append("\\r");
					case '\t' -> quoted.append("\\t");
					default -> {
						if (c < 0x20) quoted.append(String.format("\\u%04x", (int) c));
						else quoted.append(c);
					}
				}
			}
			return quoted.append('"').toString();
		}

		@Override
		public String toString() {
			return out.toString();
		}
	}
}
