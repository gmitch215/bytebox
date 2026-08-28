package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes the Java class the compiled module exports its handlers from.
 *
 * <p>A separate class rather than a member of the handler, because a static method exported as
 * {@code fetch} cannot sit beside the instance {@code fetch} that {@code Worker} declares. Keeping
 * the generated glue out of the handler also leaves the handler a plain Java class with no
 * annotations on it.
 *
 * <p>Each exported method hands JavaScript a promise straight away and starts the handler on a fiber.
 * That shape is what makes a blocking handler possible: a promise executor cannot suspend, and an
 * entry point returning a value would return before the handler's suspensions finished.
 *
 * @since 1.0.0
 */
@CacheableTask
public abstract class GenerateEntryPointTask extends DefaultTask {

	/** The package the generated entry point lives in. */
	public static final String PACKAGE = "dev.gmitch215.bytebox.generated";

	/** The generated class's simple name. */
	public static final String CLASS_NAME = "ByteboxEntry";

	/** {@return the handler class to route to} */
	@Input
	public abstract Property<String> getHandlerClass();

	/** {@return the Java classes exposed as Durable Objects} */
	@Input
	public abstract ListProperty<String> getDurableObjectClasses();

	/** {@return where to find the handler, so its interfaces can be read} */
	@Classpath
	public abstract ConfigurableFileCollection getHandlerClasspath();

	/** {@return where the generated source goes} */
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	/** Writes the entry point. */
	@TaskAction
	public void generate() {
		String handler = getHandlerClass().get();
		List<Triggers> triggers = Triggers.of(handler, getHandlerClasspath());
		List<String> exported = new ArrayList<>();
		for (Triggers trigger : triggers) exported.add(trigger.exportName());
		getLogger().lifecycle("bytebox: {} handles {}", handler, exported);

		List<DurableObjects> objects = DurableObjects.of(
			getDurableObjectClasses().get(),
			getHandlerClasspath()
		);
		for (DurableObjects object : objects) {
			getLogger().lifecycle(
				"bytebox: {} is a Durable Object{}{}",
				object.className(),
				object.sockets() ? " with sockets" : "",
				object.alarms() ? " with alarms" : ""
			);
		}

		Path directory = getOutputDirectory()
			.get()
			.getAsFile()
			.toPath()
			.resolve(PACKAGE.replace('.', '/'));
		try {
			Files.createDirectories(directory);
			Files.writeString(
				directory.resolve(CLASS_NAME + ".java"),
				source(handler, triggers, objects)
			);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private String source(String handler, List<Triggers> triggers, List<DurableObjects> objects) {
		StringBuilder out = new StringBuilder();
		out.append("package ").append(PACKAGE).append(";\n\n");
		out.append("import dev.gmitch215.bytebox.Env;\n");
		out.append("import dev.gmitch215.bytebox.ExecutionCtx;\n");
		out.append("import dev.gmitch215.bytebox.Handlers;\n");
		if (triggers.contains(Triggers.FETCH)) {
			out.append("import dev.gmitch215.bytebox.Request;\n");
			out.append("import dev.gmitch215.bytebox.Response;\n");
		}
		if (triggers.contains(Triggers.SCHEDULED)) {
			out.append("import dev.gmitch215.bytebox.ScheduledController;\n");
		}
		if (triggers.contains(Triggers.EMAIL)) {
			out.append("import dev.gmitch215.bytebox.MailMessage;\n");
		}
		if (triggers.contains(Triggers.QUEUE)) {
			out.append("import dev.gmitch215.bytebox.QueueBatch;\n");
			out.append("import dev.gmitch215.bytebox.js.TSObject;\n");
		}
		if (triggers.contains(Triggers.TAIL)) {
			out.append("import dev.gmitch215.bytebox.TraceItem;\n");
			out.append("import org.teavm.jso.core.JSArrayReader;\n");
		}
		if (!objects.isEmpty()) {
			out.append("import dev.gmitch215.bytebox.durable.DurableHandlers;\n");
			out.append("import dev.gmitch215.bytebox.durable.DurableState;\n");
			if (!triggers.contains(Triggers.FETCH)) {
				out.append("import dev.gmitch215.bytebox.Request;\n");
				out.append("import dev.gmitch215.bytebox.Response;\n");
			}
			boolean sockets = false;
			for (DurableObjects object : objects) sockets |= object.sockets();
			if (sockets) {
				out.append("import dev.gmitch215.bytebox.socket.WebSocket;\n");
				out.append("import org.teavm.jso.typedarrays.ArrayBuffer;\n");
			}
		}
		out.append("import org.teavm.jso.JSExport;\n");
		out.append("import org.teavm.jso.JSObject;\n");
		out.append("import org.teavm.jso.core.JSPromise;\n\n");

		out.append("/** Generated by the bytebox Gradle plugin. Edits are overwritten. */\n");
		out.append("public final class ").append(CLASS_NAME).append(" {\n\n");
		out.append("\tprivate static final ")
			.append(handler)
			.append(" HANDLER = new ")
			.append(handler)
			.append("();\n\n");
		out.append("\tprivate ").append(CLASS_NAME).append("() {}\n");

		for (Triggers trigger : triggers) out.append('\n').append(export(trigger));
		for (DurableObjects object : objects) out.append('\n').append(durable(object));

		// the generated Worker calls this during module evaluation, which is the only place a
		// registration can run: a handler that needs a codec is already inside a request
		out.append(
			"\n\t/** Registers what the generators wrote. Called during module evaluation. */\n"
		);
		out.append("\tpublic static void main(String[] args) {\n");
		out.append("\t\t").append(GenerateCodecsTask.REGISTRY).append(".register();\n");
		out.append("\t\t").append(GenerateSerialCodecsTask.REGISTRY).append(".register();\n");
		out.append("\t}\n}\n");
		return out.toString();
	}

	/**
	 * The exports one Durable Object's generated JavaScript class calls.
	 *
	 * <p>Every one takes the instance's identifier first, because the runtime holds one JavaScript
	 * object per Durable Object instance and there has to be one Java object per instance to match. The
	 * identifier is what pairs them.
	 */
	private String durable(DurableObjects object) {
		String type = object.className();
		String prefix = object.exportPrefix();
		StringBuilder out = new StringBuilder();

		out.append("\t@JSExport\n");
		out.append("\tpublic static JSPromise<Response> ").append(prefix).append("Fetch(\n");
		out.append("\t\tString id,\n\t\tRequest request,\n\t\tDurableState state,\n\t\tEnv env\n");
		out.append("\t) {\n");
		out.append("\t\treturn DurableHandlers.fetch(")
			.append(instance(type))
			.append(", request, state, env);\n");
		out.append("\t}\n");

		if (object.alarms()) {
			out.append("\n\t@JSExport\n");
			out.append("\tpublic static JSPromise<JSObject> ").append(prefix).append("Alarm(\n");
			out.append("\t\tString id,\n\t\tDurableState state,\n\t\tEnv env\n");
			out.append("\t) {\n");
			out.append("\t\treturn DurableHandlers.alarm(")
				.append(instance(type))
				.append(", state, env);\n");
			out.append("\t}\n");
		}

		if (object.sockets()) {
			out.append("\n\t@JSExport\n");
			out.append("\tpublic static JSPromise<JSObject> ")
				.append(prefix)
				.append("MessageText(\n");
			out.append(
				"\t\tString id,\n\t\tWebSocket socket,\n\t\tString text,\n\t\tDurableState state,\n" +
					"\t\tEnv env\n"
			);
			out.append("\t) {\n");
			out.append("\t\treturn DurableHandlers.message(")
				.append(instance(type))
				.append(", socket, text, state, env);\n");
			out.append("\t}\n");

			out.append("\n\t@JSExport\n");
			out.append("\tpublic static JSPromise<JSObject> ")
				.append(prefix)
				.append("MessageBytes(\n");
			out.append(
				"\t\tString id,\n\t\tWebSocket socket,\n\t\tArrayBuffer bytes,\n" +
					"\t\tDurableState state,\n\t\tEnv env\n"
			);
			out.append("\t) {\n");
			out.append("\t\treturn DurableHandlers.message(")
				.append(instance(type))
				.append(", socket, bytes, state, env);\n");
			out.append("\t}\n");

			out.append("\n\t@JSExport\n");
			out.append("\tpublic static JSPromise<JSObject> ").append(prefix).append("Closed(\n");
			out.append(
				"\t\tString id,\n\t\tWebSocket socket,\n\t\tint code,\n\t\tString reason,\n" +
					"\t\tboolean clean,\n\t\tDurableState state,\n\t\tEnv env\n"
			);
			out.append("\t) {\n");
			out.append("\t\treturn DurableHandlers.closed(")
				.append(instance(type))
				.append(", socket, code, reason, clean, state, env);\n");
			out.append("\t}\n");

			out.append("\n\t@JSExport\n");
			out.append("\tpublic static JSPromise<JSObject> ").append(prefix).append("Failed(\n");
			out.append(
				"\t\tString id,\n\t\tWebSocket socket,\n\t\tString message,\n" +
					"\t\tDurableState state,\n\t\tEnv env\n"
			);
			out.append("\t) {\n");
			out.append("\t\treturn DurableHandlers.failed(")
				.append(instance(type))
				.append(", socket, message, state, env);\n");
			out.append("\t}\n");
		}

		return out.toString();
	}

	private static String instance(String type) {
		return "DurableHandlers.instance(id, " + type + "::new)";
	}

	private String export(Triggers trigger) {
		return switch (trigger) {
			case FETCH -> """
			\t@JSExport
			\tpublic static JSPromise<Response> fetch(Request request, Env env, ExecutionCtx ctx) {
			\t\treturn Handlers.fetch(HANDLER, request, env, ctx);
			\t}
			""";
			case SCHEDULED -> """
			\t@JSExport
			\tpublic static JSPromise<JSObject> scheduled(
			\t\tScheduledController controller,
			\t\tEnv env,
			\t\tExecutionCtx ctx
			\t) {
			\t\treturn Handlers.scheduled(HANDLER, controller, env, ctx);
			\t}
			""";
			case EMAIL -> """
			\t@JSExport
			\tpublic static JSPromise<JSObject> email(MailMessage message, Env env, ExecutionCtx ctx) {
			\t\treturn Handlers.email(HANDLER, message, env, ctx);
			\t}
			""";
			case QUEUE -> """
			\t@JSExport
			\tpublic static JSPromise<JSObject> queue(QueueBatch batch, Env env, ExecutionCtx ctx) {
			\t\treturn Handlers.queue(HANDLER, batch, env, ctx);
			\t}
			""";
			case TAIL -> """
			\t@JSExport
			\tpublic static JSPromise<JSObject> tail(
			\t\tJSArrayReader<TraceItem> events,
			\t\tEnv env,
			\t\tExecutionCtx ctx
			\t) {
			\t\treturn Handlers.tail(HANDLER, events, env, ctx);
			\t}
			""";
			case ALARM -> """
			\t@JSExport
			\tpublic static JSPromise<JSObject> alarm(Env env, ExecutionCtx ctx) {
			\t\treturn Handlers.alarm(HANDLER, env, ctx);
			\t}
			""";
		};
	}
}
