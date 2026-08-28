package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("declaring bindings")
class BindingsTest {

	@Test
	@DisplayName("gives the first of each type its own name")
	void defaultNames() {
		Bindings bindings = new Bindings();
		bindings.kv();
		bindings.d1();
		bindings.r2();
		bindings.ai();

		assertEquals(List.of("KV", "DB", "BLOB", "AI"), names(bindings));
	}

	@Test
	@DisplayName("numbers each repeat rather than refusing it")
	void numbersRepeats() {
		Bindings bindings = new Bindings();
		bindings.kv();
		bindings.kv();
		bindings.kv();

		assertEquals(List.of("KV", "KV_2", "KV_3"), names(bindings));
	}

	@Test
	@DisplayName("numbers per type, not across types")
	void numbersPerType() {
		Bindings bindings = new Bindings();
		bindings.add(BindingType.KV, BindingType.D1, BindingType.D1, BindingType.KV);

		assertEquals(List.of("KV", "DB", "DB_2", "KV_2"), names(bindings));
	}

	@Test
	@DisplayName("takes an explicit name over the default")
	void explicitName() {
		Bindings bindings = new Bindings();
		bindings.kv("SESSIONS");

		assertEquals(List.of("SESSIONS"), names(bindings));
	}

	@Test
	@DisplayName("prefixes a Durable Object with DO_ and upper-snakes the class name")
	void durableObjectNames() {
		Bindings bindings = new Bindings();
		bindings.durableObject("Counter");
		bindings.durableObject("com.example.RateLimiter");

		assertEquals(List.of("DO_COUNTER", "DO_RATE_LIMITER"), names(bindings));
	}

	@Test
	@DisplayName("keeps the Durable Object's simple class name for Wrangler")
	void durableObjectClassName() {
		Bindings bindings = new Bindings();
		bindings.durableObject("com.example.RateLimiter");

		assertEquals("RateLimiter", bindings.getAll().get(0).identifiers().get("class_name"));
	}

	@Test
	@DisplayName("refuses two bindings with the same name, because env is keyed by it")
	void refusesDuplicates() {
		Bindings bindings = new Bindings();
		bindings.kv("CACHE");

		GradleException failure = assertThrows(GradleException.class, () -> bindings.r2("CACHE"));

		assertTrue(failure.getMessage().contains("both named CACHE"), failure.getMessage());
	}

	@Test
	@DisplayName("refuses a second binding of an account-level service")
	void refusesASecondSingleton() {
		Bindings bindings = new Bindings();
		bindings.ai();

		GradleException failure = assertThrows(GradleException.class, bindings::ai);

		assertTrue(failure.getMessage().contains("only declare one AI"), failure.getMessage());
	}

	@Test
	@DisplayName("carries the identifiers each type spells its own way")
	void identifiers() {
		Bindings bindings = new Bindings();
		bindings.kv("SESSIONS", spec -> spec.setId("abc123"));
		bindings.d1("DB", spec -> {
			spec.setDatabaseName("prod");
			spec.setDatabaseId("def456");
		});
		bindings.r2("BLOB", spec -> spec.setJurisdiction("eu"));

		List<Binding> declared = bindings.getAll();
		assertEquals("abc123", declared.get(0).identifiers().get("id"));
		assertEquals("prod", declared.get(1).identifiers().get("database_name"));
		assertEquals("def456", declared.get(1).identifiers().get("database_id"));
		assertEquals("eu", declared.get(2).identifiers().get("jurisdiction"));
	}

	@Test
	@DisplayName("leaves identifiers out when none were given, so Wrangler provisions")
	void noIdentifiers() {
		Bindings bindings = new Bindings();
		bindings.kv();

		assertTrue(bindings.getAll().get(0).identifiers().isEmpty());
	}

	@Test
	@DisplayName("names a Durable Object under name rather than binding, as Wrangler wants")
	void durableObjectConfigKey() {
		Bindings bindings = new Bindings();
		bindings.durableObject("Counter");

		assertTrue(bindings.getAll().get(0).toConfig().containsKey("name"));
	}

	@Test
	@DisplayName("names everything else under binding")
	void ordinaryConfigKey() {
		Bindings bindings = new Bindings();
		bindings.kv();

		assertTrue(bindings.getAll().get(0).toConfig().containsKey("binding"));
	}

	private static List<String> names(Bindings bindings) {
		return bindings.getAll().stream().map(Binding::name).toList();
	}
}
