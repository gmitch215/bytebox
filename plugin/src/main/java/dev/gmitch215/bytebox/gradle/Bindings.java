package dev.gmitch215.bytebox.gradle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.GradleException;

/**
 * The {@code bindings { }} block.
 *
 * <p>Names are optional. The first binding of a type takes the type's own name and each repeat gains
 * a number, so a project declaring one of each never writes a name down and a project declaring two
 * KV namespaces gets {@code KV} and {@code KV_2}.
 *
 * {@snippet lang = "kotlin":
 * bindings {
 * 	kv()                                    // KV
 * 	kv()                                    // KV_2
 * 	kv("SESSIONS") { id = "abc123" }        // an explicit name and a remote id
 * 	d1()                                    // DB
 * 	r2()                                    // BLOB
 * 	durableObject("Counter")                // DO_COUNTER
 * }
 *}
 *
 * @since 1.0.0
 */
public class Bindings {

	private final List<Binding> declared = new ArrayList<>();
	private final Map<BindingType, Integer> counts = new EnumMap<>(BindingType.class);

	/** {@return every binding declared so far, in declaration order} */
	public List<Binding> getAll() {
		return List.copyOf(declared);
	}

	/**
	 * Declares several bindings at once, each taking its default name.
	 *
	 * @param types the types, repeated as often as the Worker needs them
	 */
	public void add(BindingType... types) {
		for (BindingType type : types) declare(type, null, Map.of());
	}

	/** Declares a KV namespace named {@code KV}. */
	public void kv() {
		declare(BindingType.KV, null, Map.of());
	}

	/**
	 * @param name the binding name
	 */
	public void kv(String name) {
		declare(BindingType.KV, name, Map.of());
	}

	/**
	 * @param name the binding name
	 * @param spec {@code id} and {@code previewId}
	 */
	public void kv(String name, Action<KvSpec> spec) {
		KvSpec options = new KvSpec();
		spec.execute(options);
		declare(BindingType.KV, name, options.identifiers());
	}

	/** Declares a D1 database named {@code DB}. */
	public void d1() {
		declare(BindingType.D1, null, Map.of());
	}

	/**
	 * @param name the binding name
	 */
	public void d1(String name) {
		declare(BindingType.D1, name, Map.of());
	}

	/**
	 * @param name the binding name
	 * @param spec {@code databaseName}, {@code databaseId}, {@code migrationsDir}
	 */
	public void d1(String name, Action<D1Spec> spec) {
		D1Spec options = new D1Spec();
		spec.execute(options);
		declare(BindingType.D1, name, options.identifiers());
	}

	/** Declares an R2 bucket named {@code BLOB}. */
	public void r2() {
		declare(BindingType.R2, null, Map.of());
	}

	/**
	 * @param name the binding name
	 */
	public void r2(String name) {
		declare(BindingType.R2, name, Map.of());
	}

	/**
	 * @param name the binding name
	 * @param spec {@code bucketName} and {@code jurisdiction}
	 */
	public void r2(String name, Action<R2Spec> spec) {
		R2Spec options = new R2Spec();
		spec.execute(options);
		declare(BindingType.R2, name, options.identifiers());
	}

	/** Declares the Workers AI binding, named {@code AI}. */
	public void ai() {
		declare(BindingType.AI, null, Map.of());
	}

	/** Declares a Queue producer named {@code QUEUE}. */
	public void queue() {
		declare(BindingType.QUEUE, null, Map.of());
	}

	/**
	 * @param name the binding name, which is also the queue name unless one is given
	 */
	public void queue(String name) {
		declare(BindingType.QUEUE, name, Map.of("queue", name));
	}

	/**
	 * @param name the binding name
	 * @param queue the queue's own name
	 */
	public void queue(String name, String queue) {
		declare(BindingType.QUEUE, name, Map.of("queue", queue));
	}

	/**
	 * Declares a Durable Object namespace, named {@code DO_} and the class name upper-snaked.
	 *
	 * @param className the Durable Object class, simple name or fully qualified
	 */
	public void durableObject(String className) {
		String simple = className.substring(className.lastIndexOf('.') + 1);
		declare(
			BindingType.DURABLE_OBJECT,
			"DO_" + upperSnake(simple),
			Map.of("class_name", simple)
		);
	}

	/**
	 * Declares a Durable Object living in another Worker.
	 *
	 * @param className the Durable Object class
	 * @param scriptName the Worker that defines it
	 */
	public void durableObject(String className, String scriptName) {
		String simple = className.substring(className.lastIndexOf('.') + 1);
		Map<String, String> identifiers = new LinkedHashMap<>();
		identifiers.put("class_name", simple);
		identifiers.put("script_name", scriptName);
		declare(BindingType.DURABLE_OBJECT, "DO_" + upperSnake(simple), identifiers);
	}

	/** Declares a static assets binding named {@code ASSETS}, serving {@code ./public}. */
	public void assets() {
		assets("./public");
	}

	/**
	 * @param directory the directory to serve
	 */
	public void assets(String directory) {
		declare(BindingType.ASSETS, null, Map.of("directory", directory));
	}

	/**
	 * Declares a service binding to another Worker.
	 *
	 * @param service the Worker's name, which is also the binding name
	 */
	public void service(String service) {
		declare(BindingType.SERVICE, upperSnake(service), Map.of("service", service));
	}

	/**
	 * @param name the binding name
	 * @param service the Worker's name
	 */
	public void service(String name, String service) {
		declare(BindingType.SERVICE, name, Map.of("service", service));
	}

	/** Declares a Vectorize index named {@code VECTORIZE}. */
	public void vectorize(String indexName) {
		declare(BindingType.VECTORIZE, null, Map.of("index_name", indexName));
	}

	/**
	 * @param name the binding name
	 * @param indexName the index's own name
	 */
	public void vectorize(String name, String indexName) {
		declare(BindingType.VECTORIZE, name, Map.of("index_name", indexName));
	}

	/**
	 * Declares a Hyperdrive configuration named {@code HYPERDRIVE}.
	 *
	 * @param id the configuration id
	 */
	public void hyperdrive(String id) {
		declare(BindingType.HYPERDRIVE, null, Map.of("id", id));
	}

	/**
	 * Declares an Analytics Engine dataset named {@code ANALYTICS}.
	 *
	 * @param dataset the dataset name
	 */
	public void analytics(String dataset) {
		declare(BindingType.ANALYTICS, null, Map.of("dataset", dataset));
	}

	/**
	 * Declares an email sending binding named {@code EMAIL}.
	 *
	 * @param destination the verified destination address
	 */
	public void email(String destination) {
		declare(BindingType.EMAIL, null, Map.of("destination_address", destination));
	}

	/** Declares the Images binding, named {@code IMAGES}. */
	public void images() {
		declare(BindingType.IMAGES, null, Map.of());
	}

	/** Declares the Browser Rendering binding, named {@code BROWSER}. */
	public void browser() {
		declare(BindingType.BROWSER, null, Map.of());
	}

	/** Declares the version metadata binding, named {@code CF_VERSION_METADATA}. */
	public void versionMetadata() {
		declare(BindingType.VERSION_METADATA, null, Map.of());
	}

	/**
	 * Declares a Workflow named {@code WORKFLOW}.
	 *
	 * @param className the class extending Cloudflare's workflow entrypoint
	 */
	public void workflow(String className) {
		String simple = className.substring(className.lastIndexOf('.') + 1);
		declare(BindingType.WORKFLOW, null, Map.of("class_name", simple));
	}

	/**
	 * Declares a rate limiter named {@code RATELIMIT}.
	 *
	 * @param namespaceId the rate limit namespace
	 * @param limit how many requests are allowed
	 * @param periodSeconds the window, 10 or 60
	 */
	public void rateLimit(int namespaceId, int limit, int periodSeconds) {
		Map<String, String> identifiers = new LinkedHashMap<>();
		identifiers.put("namespace_id", String.valueOf(namespaceId));
		identifiers.put(
			"simple",
			"{ \"limit\": " + limit + ", \"period\": " + periodSeconds + " }"
		);
		declare(BindingType.RATELIMIT, null, identifiers);
	}

	/**
	 * Declares a binding of any type, for one this block has no method for yet.
	 *
	 * @param type the type
	 * @param name the binding name, or {@code null} for the default
	 * @param identifiers the Wrangler keys
	 */
	public void any(BindingType type, String name, Map<String, String> identifiers) {
		declare(type, name, identifiers);
	}

	private void declare(BindingType type, String name, Map<String, String> identifiers) {
		int seen = counts.merge(type, 1, Integer::sum);
		if (seen > 1 && !type.repeatable()) {
			throw new GradleException(
				"a Worker can only declare one " +
					type.defaultName() +
					" binding, because it is an account-level service rather than a named resource"
			);
		}
		String resolved = name != null ? name : numbered(type.defaultName(), seen);
		for (Binding existing : declared) {
			if (existing.name().equals(resolved)) {
				throw new GradleException(
					"two bindings are both named " +
						resolved +
						"; a binding name is what env is keyed by, so it has to be unique"
				);
			}
		}
		declared.add(new Binding(type, resolved, identifiers));
	}

	private static String numbered(String base, int seen) {
		return seen == 1 ? base : base + "_" + seen;
	}

	private static String upperSnake(String name) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isUpperCase(c) && i > 0 && !isBoundary(name.charAt(i - 1))) {
				out.append('_');
			}
			out.append(isBoundary(c) ? '_' : Character.toUpperCase(c));
		}
		return out.toString().replaceAll("_+", "_");
	}

	private static boolean isBoundary(char c) {
		return c == '-' || c == '_' || c == ' ' || c == '.';
	}

	/** Identifiers a KV namespace can carry. */
	public static class KvSpec {

		private String id;
		private String previewId;

		/** {@return the namespace id, or null to let Wrangler provision one} */
		public String getId() {
			return id;
		}

		/** @param id the namespace id */
		public void setId(String id) {
			this.id = id;
		}

		/** {@return the namespace used by {@code wrangler dev}} */
		public String getPreviewId() {
			return previewId;
		}

		/** @param previewId the namespace used by {@code wrangler dev} */
		public void setPreviewId(String previewId) {
			this.previewId = previewId;
		}

		Map<String, String> identifiers() {
			Map<String, String> identifiers = new LinkedHashMap<>();
			if (id != null) identifiers.put("id", id);
			if (previewId != null) identifiers.put("preview_id", previewId);
			return identifiers;
		}
	}

	/** Identifiers a D1 database can carry. */
	public static class D1Spec {

		private String databaseName;
		private String databaseId;
		private String migrationsDir;

		/** {@return the database's own name} */
		public String getDatabaseName() {
			return databaseName;
		}

		/** @param databaseName the database's own name */
		public void setDatabaseName(String databaseName) {
			this.databaseName = databaseName;
		}

		/** {@return the database id} */
		public String getDatabaseId() {
			return databaseId;
		}

		/** @param databaseId the database id */
		public void setDatabaseId(String databaseId) {
			this.databaseId = databaseId;
		}

		/** {@return where the migrations live} */
		public String getMigrationsDir() {
			return migrationsDir;
		}

		/** @param migrationsDir where the migrations live */
		public void setMigrationsDir(String migrationsDir) {
			this.migrationsDir = migrationsDir;
		}

		Map<String, String> identifiers() {
			Map<String, String> identifiers = new LinkedHashMap<>();
			if (databaseName != null) identifiers.put("database_name", databaseName);
			if (databaseId != null) identifiers.put("database_id", databaseId);
			if (migrationsDir != null) identifiers.put("migrations_dir", migrationsDir);
			return identifiers;
		}
	}

	/** Identifiers an R2 bucket can carry. */
	public static class R2Spec {

		private String bucketName;
		private String jurisdiction;

		/** {@return the bucket's own name} */
		public String getBucketName() {
			return bucketName;
		}

		/** @param bucketName the bucket's own name */
		public void setBucketName(String bucketName) {
			this.bucketName = bucketName;
		}

		/** {@return the jurisdiction the bucket is pinned to, such as {@code eu}} */
		public String getJurisdiction() {
			return jurisdiction;
		}

		/** @param jurisdiction the jurisdiction the bucket is pinned to */
		public void setJurisdiction(String jurisdiction) {
			this.jurisdiction = jurisdiction;
		}

		Map<String, String> identifiers() {
			Map<String, String> identifiers = new LinkedHashMap<>();
			if (bucketName != null) identifiers.put("bucket_name", bucketName);
			if (jurisdiction != null) identifiers.put("jurisdiction", jurisdiction);
			return identifiers;
		}
	}

	/** {@return the locale-independent upper-snake form of a name, exposed for the tests} */
	static String snake(String name) {
		return upperSnake(name).toUpperCase(Locale.ROOT);
	}
}
