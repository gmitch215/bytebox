package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** What a Kotlin {@code object} compiles to: a private constructor and a public static instance. */
public final class KotlinObject implements Worker {

	public static final KotlinObject INSTANCE = new KotlinObject();

	private KotlinObject() {}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return null;
	}
}
