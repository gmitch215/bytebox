package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Stubs.StubEnv;
import dev.gmitch215.bytebox.Stubs.Value;
import dev.gmitch215.bytebox.binding.Ai;
import dev.gmitch215.bytebox.binding.AiSearch;
import dev.gmitch215.bytebox.binding.AnalyticsEngine;
import dev.gmitch215.bytebox.binding.Container;
import dev.gmitch215.bytebox.binding.D1Database;
import dev.gmitch215.bytebox.binding.DurableObjectNamespace;
import dev.gmitch215.bytebox.binding.EmailSender;
import dev.gmitch215.bytebox.binding.Fetcher;
import dev.gmitch215.bytebox.binding.Hyperdrive;
import dev.gmitch215.bytebox.binding.Images;
import dev.gmitch215.bytebox.binding.Pipeline;
import dev.gmitch215.bytebox.binding.Queue;
import dev.gmitch215.bytebox.binding.RateLimit;
import dev.gmitch215.bytebox.binding.SecretsStore;
import dev.gmitch215.bytebox.binding.Vectorize;
import dev.gmitch215.bytebox.binding.VersionMetadata;
import dev.gmitch215.bytebox.binding.Workflow;
import dev.gmitch215.bytebox.js.TSObject;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A resolved binding is absent here on purpose: each accessor ends in a cast javac checks at the call
 * site, and the stub would have to be both a {@code TSObject} and the binding interface, which cannot
 * be done for a type declaring {@code get(String)} with its own return type. The workerd lane resolves
 * them against real bindings.
 *
 * <p>What is checkable here is the name each accessor asks for, which is the half that can drift: the
 * plugin decides those names and nothing else compares the two lists.
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

	@Test
	@DisplayName("resolves each binding under the default name the plugin assigns")
	void defaultNames() {
		StubEnv env = new StubEnv()
			.with("DB", binding(D1Database.class))
			.with("AI", binding(Ai.class))
			.with("QUEUE", binding(Queue.class))
			.with("SERVICE", binding(Fetcher.class))
			.with("ASSETS", binding(Fetcher.class))
			.with("MTLS", binding(Fetcher.class))
			.with("BROWSER", binding(Fetcher.class))
			.with("VECTORIZE", binding(Vectorize.class))
			.with("HYPERDRIVE", binding(Hyperdrive.class))
			.with("ANALYTICS", binding(AnalyticsEngine.class))
			.with("EMAIL", binding(EmailSender.class))
			.with("IMAGES", binding(Images.class))
			.with("WORKFLOW", binding(Workflow.class))
			.with("RATELIMIT", binding(RateLimit.class))
			.with("CF_VERSION_METADATA", binding(VersionMetadata.class))
			.with("SECRETS", binding(SecretsStore.class))
			.with("PIPELINE", binding(Pipeline.class))
			.with("AI_SEARCH", binding(AiSearch.class));

		assertNotNull(env.d1());
		assertNotNull(env.ai());
		assertNotNull(env.queue());
		assertNotNull(env.service());
		assertNotNull(env.assets());
		assertNotNull(env.mtls());
		assertNotNull(env.browser());
		assertNotNull(env.vectorize());
		assertNotNull(env.hyperdrive());
		assertNotNull(env.analytics());
		assertNotNull(env.email());
		assertNotNull(env.images());
		assertNotNull(env.workflow());
		assertNotNull(env.rateLimit());
		assertNotNull(env.version());
		assertNotNull(env.secret());
		assertNotNull(env.pipeline());
		assertNotNull(env.aiSearch());
	}

	@Test
	@DisplayName("takes an explicit name over the default, for every type that can be repeated")
	void explicitNames() {
		StubEnv env = new StubEnv()
			.with("DB_2", binding(D1Database.class))
			.with("QUEUE_2", binding(Queue.class))
			.with("DO_COUNTER", binding(DurableObjectNamespace.class))
			.with("SERVICE_2", binding(Fetcher.class))
			.with("VECTORIZE_2", binding(Vectorize.class))
			.with("HYPERDRIVE_2", binding(Hyperdrive.class))
			.with("EMAIL_2", binding(EmailSender.class))
			.with("WORKFLOW_2", binding(Workflow.class))
			.with("RATELIMIT_2", binding(RateLimit.class))
			.with("SECRETS_2", binding(SecretsStore.class))
			.with("CONTAINER", binding(Container.class))
			.with("AI_SEARCH_2", binding(AiSearch.class))
			.with("DISPATCH", binding(Ai.class));

		assertNotNull(env.d1("DB_2"));
		assertNotNull(env.queue("QUEUE_2"));
		assertNotNull(env.durableObject("DO_COUNTER"));
		assertNotNull(env.service("SERVICE_2"));
		assertNotNull(env.vectorize("VECTORIZE_2"));
		assertNotNull(env.hyperdrive("HYPERDRIVE_2"));
		assertNotNull(env.email("EMAIL_2"));
		assertNotNull(env.workflow("WORKFLOW_2"));
		assertNotNull(env.rateLimit("RATELIMIT_2"));
		assertNotNull(env.secret("SECRETS_2"));
		assertNotNull(env.container("CONTAINER"));
		assertNotNull(env.aiSearch("AI_SEARCH_2"));
	}

	@Test
	@DisplayName("names every binding a project can declare, so the accessor and the plugin agree")
	void namesMatchTheDeclaredDefaults() {
		StubEnv env = new StubEnv();

		assertAsksFor("KV", env::kv);
		assertAsksFor("BLOB", env::r2);
		assertAsksFor("DISPATCH", env::dispatch);
		assertAsksFor("KV_2", () -> env.kv("KV_2"));
		assertAsksFor("BLOB_2", () -> env.r2("BLOB_2"));
	}

	/**
	 * A binding that is present but has no behaviour, which is all the accessors need.
	 *
	 * <p>A proxy rather than a written stub, because the object has to be both a {@code TSObject} and
	 * the binding interface at once and there are twenty of those. It cannot be one for
	 * {@code KVNamespace}, {@code R2Bucket} or {@code DispatchNamespace}: each declares its own
	 * {@code get(String)} with a different return type, which no single object can have.
	 */
	private static TSObject binding(Class<?> type) {
		return (TSObject) Proxy.newProxyInstance(
			EnvTest.class.getClassLoader(),
			new Class<?>[] { TSObject.class, type },
			(proxy, method, arguments) ->
				switch (method.getName()) {
					case "isNull", "isUndefined" -> false;
					case "equals" -> proxy == arguments[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> "a " + type.getSimpleName();
					default -> throw new UnsupportedOperationException(method.getName());
				}
		);
	}

	/** An undeclared binding names itself, so the accessor's own name shows up in the failure. */
	private static void assertAsksFor(String name, Runnable accessor) {
		IllegalStateException failure = assertThrows(IllegalStateException.class, accessor::run);

		assertEquals(
			"no binding named " + name + " is declared; add it to the bytebox bindings block",
			failure.getMessage()
		);
	}
}
