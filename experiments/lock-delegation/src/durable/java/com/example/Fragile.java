package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.durable.DurableObject;
import dev.gmitch215.bytebox.durable.DurableState;

/** A Durable Object whose first construction throws after it has waited, and later ones succeed. */
public class Fragile implements DurableObject {

	private static int attempts;

	private final int attempt;

	public Fragile() {
		attempt = ++attempts;
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		if (attempt == 1) throw new IllegalStateException("first attempt fails");
	}

	@Override
	public Response fetch(Request request, DurableState state, Env env) {
		return Bytebox.response("fragile attempt " + attempt);
	}
}
