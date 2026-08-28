package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

/**
 * Calls an npm package from Java.
 *
 * <p>An npm package is JavaScript, so it never enters the WebAssembly. It costs bundle bytes rather
 * than module bytes, and the call is an ordinary Java method.
 *
 * <p>{@code fromModule} is what makes it resolvable. The package name ends up in a WebAssembly custom
 * section, which no bundler can follow, so the plugin emits a static import beside the loader and
 * hands the module in by name. Declaring {@code npm("nanoid", "^5.0.9")} is what puts it in the
 * generated package.json and in that import.
 */
public class IdWorker implements Worker {

	@JSBody(
		params = "size",
		imports = @JSBodyImport(alias = "nanoid", fromModule = "nanoid"),
		script = "return nanoid.nanoid(size);"
	)
	private static native String id(int size);

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		int size = Integer.parseInt(request.query("size", "21"));
		return Bytebox.response(id(size) + "\n");
	}
}
