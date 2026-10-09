package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** Keeps a shared instance and still has a public constructor, so it is built the usual way. */
public class OpenInstance implements Worker {

	public static final OpenInstance INSTANCE = new OpenInstance();

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return null;
	}
}
