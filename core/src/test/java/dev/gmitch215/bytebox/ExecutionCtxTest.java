package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.concurrent.Future;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * What the context hands the platform. The platform's own {@code waitUntil} takes a promise and
 * nothing else, so every convenience here ends in the same call with the promise underneath.
 */
@DisplayName("the invocation context")
class ExecutionCtxTest {

	@Test
	@DisplayName("hands a promise straight through")
	void promise() {
		StubCtx ctx = new StubCtx();
		JSPromise<JSObject> promise = null;

		ctx.waitUntil(promise);

		assertEquals(1, ctx.held.size());
		assertSame(promise, ctx.held.get(0));
	}

	@Test
	@DisplayName("unwraps a future to the promise underneath it")
	void future() {
		StubCtx ctx = new StubCtx();
		Future<JSObject> work = Future.of(null);

		ctx.waitUntil(work);

		assertEquals(1, ctx.held.size());
		assertSame(work.promise(), ctx.held.get(0));
	}

	@Test
	@DisplayName("passes an exception through to the origin when it is told to")
	void passesThrough() {
		StubCtx ctx = new StubCtx();

		ctx.passThroughOnException();

		assertTrue(ctx.passedThrough);
	}

	private static final class StubCtx implements ExecutionCtx {

		final List<JSObject> held = new ArrayList<>();
		boolean passedThrough;

		@Override
		public void waitUntil(JSObject work) {
			held.add(work);
		}

		@Override
		public void passThroughOnException() {
			passedThrough = true;
		}

		@Override
		public TSObject getProps() {
			return null;
		}
	}
}
