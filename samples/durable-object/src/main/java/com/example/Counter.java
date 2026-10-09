package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.durable.DurableObject;
import dev.gmitch215.bytebox.durable.DurableState;

/**
 * The Durable Object: one instance per id, with storage that only that instance sees.
 *
 * <p>Each request adds one to the stored count and answers the new value. The count lives in the
 * instance's own storage, so it survives the instance being evicted from memory.
 */
public class Counter implements DurableObject {

	@Override
	public Response fetch(Request request, DurableState state, Env env) {
		long count = state.getLong("count", 0) + 1;
		state.put("count", count);
		return Bytebox.response(String.valueOf(count));
	}
}
