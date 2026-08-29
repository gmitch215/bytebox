package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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

	@Test
	@DisplayName("names a queue after its binding unless the queue's own name is given")
	void queues() {
		Bindings bindings = new Bindings();
		bindings.queue();
		bindings.queue("ORDERS");
		bindings.queue("SHIPPING", "shipping-queue");

		List<Binding> declared = bindings.getAll();
		assertEquals(List.of("QUEUE", "ORDERS", "SHIPPING"), names(bindings));
		assertTrue(declared.get(0).identifiers().isEmpty());
		assertEquals("ORDERS", declared.get(1).identifiers().get("queue"));
		assertEquals("shipping-queue", declared.get(2).identifiers().get("queue"));
	}

	@Test
	@DisplayName("names the Worker that defines a Durable Object living elsewhere")
	void durableObjectInAnotherWorker() {
		Bindings bindings = new Bindings();
		bindings.durableObject("com.example.Counter", "other-worker");

		Binding declared = bindings.getAll().get(0);
		assertEquals("DO_COUNTER", declared.name());
		assertEquals("Counter", declared.identifiers().get("class_name"));
		assertEquals("other-worker", declared.identifiers().get("script_name"));
	}

	@Test
	@DisplayName("serves ./public by default and whatever directory is given otherwise")
	void assets() {
		Bindings bindings = new Bindings();
		bindings.assets();

		assertEquals("ASSETS", bindings.getAll().get(0).name());
		assertEquals("./public", bindings.getAll().get(0).identifiers().get("directory"));

		Bindings elsewhere = new Bindings();
		elsewhere.assets("./site");
		assertEquals("./site", elsewhere.getAll().get(0).identifiers().get("directory"));
	}

	@Test
	@DisplayName("names a service binding after the Worker it reaches")
	void services() {
		Bindings bindings = new Bindings();
		bindings.service("auth-worker");
		bindings.service("BILLING", "billing-worker");

		List<Binding> declared = bindings.getAll();
		assertEquals("AUTH_WORKER", declared.get(0).name());
		assertEquals("auth-worker", declared.get(0).identifiers().get("service"));
		assertEquals("BILLING", declared.get(1).name());
		assertEquals("billing-worker", declared.get(1).identifiers().get("service"));
	}

	@Test
	@DisplayName("carries the identifier every remaining type spells its own way")
	void everyOtherType() {
		Bindings bindings = new Bindings();
		bindings.vectorize("docs");
		bindings.vectorize("EMBEDDINGS", "embeddings");
		bindings.hyperdrive("hd-1");
		bindings.analytics("events");
		bindings.email("ops@example.com");
		bindings.images();
		bindings.browser();
		bindings.versionMetadata();
		bindings.workflow("com.example.Onboarding");
		bindings.rateLimit(1001, 100, 60);

		List<Binding> declared = bindings.getAll();
		assertEquals("docs", declared.get(0).identifiers().get("index_name"));
		assertEquals("EMBEDDINGS", declared.get(1).name());
		assertEquals("hd-1", declared.get(2).identifiers().get("id"));
		assertEquals("events", declared.get(3).identifiers().get("dataset"));
		assertEquals("ops@example.com", declared.get(4).identifiers().get("destination_address"));
		assertEquals("IMAGES", declared.get(5).name());
		assertEquals("BROWSER", declared.get(6).name());
		assertEquals("CF_VERSION_METADATA", declared.get(7).name());
		assertEquals("Onboarding", declared.get(8).identifiers().get("class_name"));
		assertEquals("1001", declared.get(9).identifiers().get("namespace_id"));
		assertEquals(
			"{ \"limit\": 100, \"period\": 60 }",
			declared.get(9).identifiers().get("simple")
		);
	}

	@Test
	@DisplayName("takes a type the block has no method for yet")
	void anyType() {
		Bindings bindings = new Bindings();
		bindings.any(BindingType.MTLS, null, Map.of("certificate_id", "cert-1"));
		bindings.any(BindingType.PIPELINE, "EVENTS", Map.of());

		assertEquals(List.of("MTLS", "EVENTS"), names(bindings));
		assertEquals("cert-1", bindings.getAll().get(0).identifiers().get("certificate_id"));
	}

	@Test
	@DisplayName("names a Workflow under name rather than binding, as Wrangler wants")
	void workflowConfigKey() {
		Bindings bindings = new Bindings();
		bindings.workflow("com.example.Onboarding");

		assertTrue(bindings.getAll().get(0).toConfig().containsKey("name"));
	}

	@Test
	@DisplayName("puts the name first in the entry, then the identifiers as given")
	void configOrder() {
		Binding binding = new Binding(BindingType.KV, "SESSIONS", Map.of("id", "abc"));

		assertEquals(List.of("binding", "id"), List.copyOf(binding.toConfig().keySet()));
		assertEquals(Map.of(), new Binding(BindingType.KV, "KV").identifiers());
	}

	@Test
	@DisplayName("upper-snakes across every word boundary a name can use")
	void snakeCase() {
		assertEquals("RATE_LIMITER", Bindings.upperSnake("RateLimiter"));
		assertEquals("AUTH_WORKER", Bindings.upperSnake("auth-worker"));
		assertEquals("MY_APP", Bindings.upperSnake("my.app"));
		assertEquals("TWO_WORDS", Bindings.upperSnake("two words"));
		assertEquals("COUNTER", Bindings.upperSnake("Counter"));
		assertEquals("A1_STORE", Bindings.upperSnake("A1Store"));
	}

	@Test
	@DisplayName("keeps an acronym one word rather than splitting every letter of it")
	void acronyms() {
		assertEquals("HTTP_CLIENT", Bindings.upperSnake("HTTPClient"));
		assertEquals("SQL_STORE", Bindings.upperSnake("SQLStore"));
		assertEquals("HTTPCACHE", Bindings.upperSnake("HTTPCACHE"));
		assertEquals("PARSE_HTML", Bindings.upperSnake("parseHTML"));
	}

	@Test
	@DisplayName("derives a Durable Object's name the same way in both places that derive it")
	void oneDerivation() {
		for (String simple : List.of("Counter", "RateLimiter", "HTTPCache", "SQLStore")) {
			Bindings bindings = new Bindings();
			bindings.durableObject("com.example." + simple);

			assertEquals(
				new DurableObjects("com.example." + simple, false, false).bindingName(),
				bindings.getAll().get(0).name(),
				simple + " is named two different things"
			);
		}
	}

	@Test
	@DisplayName("keeps every identifier a spec was given and leaves out the ones it was not")
	void specs() {
		Bindings.KvSpec kv = new Bindings.KvSpec();
		kv.setId("abc");
		kv.setPreviewId("def");
		assertEquals("abc", kv.getId());
		assertEquals("def", kv.getPreviewId());

		Bindings.D1Spec d1 = new Bindings.D1Spec();
		d1.setDatabaseName("prod");
		d1.setDatabaseId("db-1");
		d1.setMigrationsDir("./migrations");
		assertEquals("prod", d1.getDatabaseName());
		assertEquals("db-1", d1.getDatabaseId());
		assertEquals("./migrations", d1.getMigrationsDir());

		Bindings.R2Spec r2 = new Bindings.R2Spec();
		r2.setBucketName("blobs");
		r2.setJurisdiction("eu");
		assertEquals("blobs", r2.getBucketName());
		assertEquals("eu", r2.getJurisdiction());

		Bindings bindings = new Bindings();
		bindings.kv("SESSIONS", spec -> spec.setPreviewId("preview"));
		bindings.d1("DB", spec -> spec.setMigrationsDir("./migrations"));
		bindings.r2("BLOB", spec -> spec.setBucketName("blobs"));

		List<Binding> declared = bindings.getAll();
		assertEquals(Map.of("preview_id", "preview"), declared.get(0).identifiers());
		assertEquals(Map.of("migrations_dir", "./migrations"), declared.get(1).identifiers());
		assertEquals(Map.of("bucket_name", "blobs"), declared.get(2).identifiers());
	}

	private static List<String> names(Bindings bindings) {
		return bindings.getAll().stream().map(Binding::name).toList();
	}
}
