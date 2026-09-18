package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * Tries every shape that could make the runtime build a Java wrapper for a JavaScript value.
 *
 * <p>The runtime keeps its wrapper caches in weak maps keyed by objects, except one plain map keyed
 * by primitives. A primitive cannot be held weakly, so that map's only cleanup is a finalizer, which
 * makes it the one structure that can grow without bound. Each step below prints a marker, so an
 * instrumented host can attribute a cache write to the operation that caused it.
 */
public class WrapperWorker implements Worker {

	private static final int N = Integer.parseInt(System.getProperty("bytebox.n", "500"));

	static {
		step("baseline-null-check", () -> {
			int seen = 0;
			for (int i = 0; i < N; i++) if (string(i) != null) seen++;
			return seen;
		});

		step("as-Object-then-hashCode", () -> {
			int sum = 0;
			for (int i = 0; i < N; i++) {
				Object o = string(i);
				sum += o.hashCode();
			}
			return sum;
		});

		step("as-Object-then-toString", () -> {
			int len = 0;
			for (int i = 0; i < N; i++) {
				Object o = string(i);
				len += String.valueOf(o).length();
			}
			return len;
		});

		step("as-Object-then-equals", () -> {
			int same = 0;
			for (int i = 0; i < N; i++) {
				Object o = string(i);
				if (o.equals("key-0")) same++;
			}
			return same;
		});

		step("into-HashMap-key", () -> {
			Map<Object, Integer> map = new HashMap<>();
			for (int i = 0; i < N; i++) map.put(string(i), i);
			return map.size();
		});

		step("into-List-then-contains", () -> {
			List<Object> list = new ArrayList<>();
			for (int i = 0; i < N; i++) list.add(string(i));
			return list.contains(string(0)) ? list.size() : -1;
		});

		step("number-as-Object", () -> {
			int sum = 0;
			for (int i = 0; i < N; i++) {
				Object o = number(i);
				sum += o.hashCode();
			}
			return sum;
		});

		step("object-as-Object", () -> {
			int sum = 0;
			for (int i = 0; i < N; i++) {
				Object o = object(i);
				sum += o.hashCode();
			}
			return sum;
		});

		System.out.println("STEP done");
	}

	/** A distinct JavaScript string per call. */
	@JSBody(params = "i", script = "return 'key-' + i;")
	private static native JSObject string(int i);

	/** A distinct JavaScript number per call. */
	@JSBody(params = "i", script = "return i + 0.5;")
	private static native JSObject number(int i);

	/** A distinct JavaScript object per call. */
	@JSBody(params = "i", script = "return { id: i };")
	private static native JSObject object(int i);

	private static void step(String name, Step body) {
		System.out.println("STEP " + name);
		try {
			System.out.println("  = " + body.run());
		} catch (Throwable t) {
			System.out.println("  threw " + t.getClass().getName());
		}
	}

	private interface Step {
		int run();
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
