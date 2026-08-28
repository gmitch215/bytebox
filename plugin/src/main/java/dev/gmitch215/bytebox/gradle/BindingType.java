package dev.gmitch215.bytebox.gradle;

/**
 * A binding type, for the short form of the bindings block.
 *
 * {@snippet lang = "kotlin":
 * bindings(KV, D1, D1, KV) // KV, DB, DB_2, KV_2
 *}
 *
 * <p>Every constant carries the name the first binding of its type takes and the Wrangler
 * configuration key it is written under. A second binding of the same type gets {@code _2}, a third
 * {@code _3}, so a project can start with the short form and add an identifier later without
 * renaming anything.
 *
 * @since 1.0.0
 */
public enum BindingType {
	/** A Workers KV namespace. */
	KV("KV", "kv_namespaces"),
	/** A D1 database. */
	D1("DB", "d1_databases"),
	/** An R2 bucket. */
	R2("BLOB", "r2_buckets"),
	/** The Workers AI binding. */
	AI("AI", "ai"),
	/** A Vectorize index. */
	VECTORIZE("VECTORIZE", "vectorize"),
	/** A Hyperdrive configuration. */
	HYPERDRIVE("HYPERDRIVE", "hyperdrive"),
	/** The Images binding. */
	IMAGES("IMAGES", "images"),
	/** The Browser Rendering binding. */
	BROWSER("BROWSER", "browser"),
	/** A Queue producer. */
	QUEUE("QUEUE", "queues"),
	/** A Durable Object namespace. */
	DURABLE_OBJECT("DO", "durable_objects"),
	/** An email sending binding. */
	EMAIL("EMAIL", "send_email"),
	/** A static assets binding. */
	ASSETS("ASSETS", "assets"),
	/** Another Worker, reached with no network hop. */
	SERVICE("SERVICE", "services"),
	/** A Workflow. */
	WORKFLOW("WORKFLOW", "workflows"),
	/** An Analytics Engine dataset. */
	ANALYTICS("ANALYTICS", "analytics_engine_datasets"),
	/** An AI Search instance. */
	AI_SEARCH("AI_SEARCH", "ai_search"),
	/** An mTLS certificate. */
	MTLS("MTLS", "mtls_certificates"),
	/** A Workers for Platforms dispatch namespace. */
	DISPATCH("DISPATCH", "dispatch_namespaces"),
	/** A Pipeline. */
	PIPELINE("PIPELINE", "pipelines"),
	/** A container. */
	CONTAINER("CONTAINER", "containers"),
	/** A Secrets Store secret. */
	SECRETS("SECRETS", "secrets_store_secrets"),
	/** The running version's metadata. */
	VERSION_METADATA("CF_VERSION_METADATA", "version_metadata"),
	/** A rate limiter. */
	RATELIMIT("RATELIMIT", "ratelimits");

	private final String defaultName;
	private final String configKey;

	BindingType(String defaultName, String configKey) {
		this.defaultName = defaultName;
		this.configKey = configKey;
	}

	/** {@return the name the first binding of this type takes} */
	public String defaultName() {
		return defaultName;
	}

	/** {@return the Wrangler configuration key this type is written under} */
	public String configKey() {
		return configKey;
	}

	/**
	 * Whether a Worker can declare more than one of these.
	 *
	 * <p>The single ones are the account-level services: there is one Workers AI, one Images, one
	 * browser. Declaring a second is a configuration mistake rather than something to number.
	 *
	 * @return whether repeats are allowed
	 */
	public boolean repeatable() {
		return switch (this) {
			case AI, IMAGES, BROWSER, ASSETS, VERSION_METADATA -> false;
			default -> true;
		};
	}
}
