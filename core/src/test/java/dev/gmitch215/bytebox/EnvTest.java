package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Stubs.StubEnv;
import dev.gmitch215.bytebox.Stubs.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Binding resolution is absent here on purpose. Each accessor ends in a cast that javac checks at the
 * call site, so a stub would have to implement the binding interface itself, and every one of those
 * returns a {@code JSPromise}, which is a class rather than an interface. The workerd lane resolves
 * the bindings against real ones.
 */
@DisplayName("Env")
class EnvTest {

	@Test
	@DisplayName("reads a variable")
	void variable() {
		StubEnv env = new StubEnv().with("GREETING", "hello");

		assertEquals("hello", env.var("GREETING"));
	}

	@Test
	@DisplayName("answers null for a variable that was never declared")
	void undeclared() {
		assertNull(new StubEnv().var("GREETING"));
	}

	@Test
	@DisplayName("answers null for a declared variable holding null")
	void declaredNull() {
		StubEnv env = new StubEnv().with("GREETING", Value.absent());

		assertNull(env.var("GREETING"));
	}

	@Test
	@DisplayName("falls back for a variable that is absent")
	void fallback() {
		assertEquals("hi", new StubEnv().var("GREETING", "hi"));
	}

	@Test
	@DisplayName("prefers a declared variable over the fallback")
	void declaredBeatsFallback() {
		StubEnv env = new StubEnv().with("GREETING", "hello");

		assertEquals("hello", env.var("GREETING", "hi"));
	}

	@Test
	@DisplayName("reports whether a binding is declared")
	void has() {
		StubEnv env = new StubEnv().with("KV", Value.object()).with("EMPTY", Value.absent());

		assertTrue(env.has("KV"));
		assertFalse(env.has("EMPTY"));
		assertFalse(env.has("MISSING"));
	}

	@Test
	@DisplayName("names an undeclared binding rather than failing further along")
	void undeclaredBinding() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
			new StubEnv().kv()
		);

		assertTrue(failure.getMessage().contains("KV"), failure.getMessage());
		assertTrue(failure.getMessage().contains("bindings block"), failure.getMessage());
	}

	@Test
	@DisplayName("names the binding a caller asked for by name")
	void undeclaredNamedBinding() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
			new StubEnv().d1("ANALYTICS_DB")
		);

		assertTrue(failure.getMessage().contains("ANALYTICS_DB"), failure.getMessage());
	}
}
