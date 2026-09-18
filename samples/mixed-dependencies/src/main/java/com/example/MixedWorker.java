package com.example;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.List;
import java.util.StringJoiner;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

/**
 * A Java dependency and an npm package in one Worker.
 *
 * <p>The two arrive by different routes and end up in different places. Guava is compiled: it is
 * linked into the WebAssembly, and only the parts this class reaches survive dead-code elimination.
 * nanoid is not compiled at all; it stays JavaScript beside the module and is called across the
 * interop boundary.
 *
 * <p>That difference is the thing to hold on to when choosing between them. A Java library costs
 * module bytes, which is what startup is paid in. An npm package costs bundle bytes, which are
 * metered and nothing else, but every call into it crosses a boundary.
 */
public class MixedWorker implements Worker {

	private static final Splitter PAIRS = Splitter.on('&').omitEmptyStrings();
	private static final Splitter KEY_VALUE = Splitter.on('=').limit(2);

	@JSBody(
		params = "size",
		imports = @JSBodyImport(alias = "nanoid", fromModule = "nanoid"),
		script = "return nanoid.nanoid(size);"
	)
	private static native String id(int size);

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		String url = request.getUrl();
		int mark = url.indexOf('?');
		String query = mark < 0 ? "" : url.substring(mark + 1);

		// guava groups the repeats; a Map<String, String> would keep only the last of each
		ImmutableListMultimap<String, String> params = Multimaps.index(
			PAIRS.splitToList(query),
			pair -> KEY_VALUE.splitToList(pair).get(0)
		);

		StringJoiner out = new StringJoiner("\n");
		out.add("request " + id(10));
		for (String key : params.keySet()) {
			List<String> values = params.get(key);
			out.add(key + " appeared " + values.size() + " time(s)");
		}
		return Bytebox.response(out + "\n");
	}
}
