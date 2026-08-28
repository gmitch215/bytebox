package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.binding.Ai;
import dev.gmitch215.bytebox.binding.AiSearch;
import dev.gmitch215.bytebox.binding.AnalyticsEngine;
import dev.gmitch215.bytebox.binding.Container;
import dev.gmitch215.bytebox.binding.D1Database;
import dev.gmitch215.bytebox.binding.DispatchNamespace;
import dev.gmitch215.bytebox.binding.DurableObjectNamespace;
import dev.gmitch215.bytebox.binding.EmailSender;
import dev.gmitch215.bytebox.binding.Fetcher;
import dev.gmitch215.bytebox.binding.Hyperdrive;
import dev.gmitch215.bytebox.binding.Images;
import dev.gmitch215.bytebox.binding.KVNamespace;
import dev.gmitch215.bytebox.binding.Pipeline;
import dev.gmitch215.bytebox.binding.Queue;
import dev.gmitch215.bytebox.binding.R2Bucket;
import dev.gmitch215.bytebox.binding.RateLimit;
import dev.gmitch215.bytebox.binding.SecretsStore;
import dev.gmitch215.bytebox.binding.Vectorize;
import dev.gmitch215.bytebox.binding.VersionMetadata;
import dev.gmitch215.bytebox.binding.Workflow;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSIndexer;
import org.teavm.jso.JSObject;

/**
 * The bindings declared in the Wrangler configuration, addressed by their binding names.
 *
 * <p>Each accessor takes the default name the plugin assigns, so a project that declared one of each
 * never writes a name down. Declaring a type twice appends a number, and the second binding is
 * reached by naming it.
 *
 * <table border="1">
 * <caption>Default binding names</caption>
 * <tr><th>Declared</th><th>First</th><th>Second</th></tr>
 * <tr><td>{@code kv()}</td><td>{@code KV}</td><td>{@code KV_2}</td></tr>
 * <tr><td>{@code d1()}</td><td>{@code DB}</td><td>{@code DB_2}</td></tr>
 * <tr><td>{@code r2()}</td><td>{@code BLOB}</td><td>{@code BLOB_2}</td></tr>
 * <tr><td>{@code durableObject(Counter.class)}</td><td>{@code DO_COUNTER}</td><td>{@code DO_COUNTER_2}</td></tr>
 * </table>
 *
 * <p>Variables and secrets are readable during module evaluation, because they are values already
 * present on the object. Anything that performs I/O is not: Cloudflare Workers forbid I/O outside a
 * request context, so a KV read or a service call at module scope fails however it is spelled.
 *
 * {@snippet lang = "java":
 * public Response fetch(Request request, Env env, ExecutionCtx ctx) {
 * 	String cached = env.kv().get(request.getUrl());
 * 	if (cached != null) return Bytebox.response(cached);
 * 	String fresh = render(env.d1());
 * 	env.kv().put(request.getUrl(), fresh, 3600);
 * 	return Bytebox.response(fresh);
 * }
 *}
 *
 * @since 1.0.0
 */
public interface Env extends JSObject {
	/**
	 * Reads one binding by name, whatever its type.
	 *
	 * @param name the binding name
	 * @return the binding, or {@code null} when it is not declared
	 */
	@JSIndexer
	TSObject get(String name);

	/**
	 * Reads a plain variable or secret.
	 *
	 * <p>Both are strings on this object and are indistinguishable from here, which is deliberate:
	 * whether a value is a secret is a property of how it was uploaded, not of how it is read.
	 *
	 * @param name the variable name
	 * @return the value, or {@code null} when it is not declared
	 */
	default String var(String name) {
		TSObject value = get(name);
		return value == null || value.isNull() ? null : value.asString();
	}

	/**
	 * Reads a variable, falling back when it is absent.
	 *
	 * @param name the variable name
	 * @param fallback the value to use when it is absent
	 * @return the value, or the fallback
	 */
	default String var(String name, String fallback) {
		String value = var(name);
		return value == null ? fallback : value;
	}

	/** {@return whether a binding is declared} */
	default boolean has(String name) {
		TSObject value = get(name);
		return value != null && !value.isNull();
	}

	/** {@return the KV namespace named {@code KV}} */
	default KVNamespace kv() {
		return require("KV").cast();
	}

	/**
	 * @param name the binding name
	 * @return the KV namespace with that name
	 */
	default KVNamespace kv(String name) {
		return require(name).cast();
	}

	/** {@return the D1 database named {@code DB}} */
	default D1Database d1() {
		return require("DB").cast();
	}

	/**
	 * @param name the binding name
	 * @return the D1 database with that name
	 */
	default D1Database d1(String name) {
		return require(name).cast();
	}

	/** {@return the R2 bucket named {@code BLOB}} */
	default R2Bucket r2() {
		return require("BLOB").cast();
	}

	/**
	 * @param name the binding name
	 * @return the R2 bucket with that name
	 */
	default R2Bucket r2(String name) {
		return require(name).cast();
	}

	/** {@return the Workers AI binding named {@code AI}} */
	default Ai ai() {
		return require("AI").cast();
	}

	/** {@return the Queue producer named {@code QUEUE}} */
	default Queue queue() {
		return require("QUEUE").cast();
	}

	/**
	 * @param name the binding name
	 * @return the Queue producer with that name
	 */
	default Queue queue(String name) {
		return require(name).cast();
	}

	/**
	 * @param name the binding name, {@code DO_} followed by the class's simple name upper-snaked
	 * @return the Durable Object namespace with that name
	 */
	default DurableObjectNamespace durableObject(String name) {
		return require(name).cast();
	}

	/** {@return the service binding named {@code SERVICE}} */
	default Fetcher service() {
		return require("SERVICE").cast();
	}

	/**
	 * @param name the binding name
	 * @return the service binding with that name
	 */
	default Fetcher service(String name) {
		return require(name).cast();
	}

	/** {@return the static assets binding named {@code ASSETS}} */
	default Fetcher assets() {
		return require("ASSETS").cast();
	}

	/** {@return the mTLS binding named {@code MTLS}} */
	default Fetcher mtls() {
		return require("MTLS").cast();
	}

	/** {@return the Browser Rendering binding named {@code BROWSER}} */
	default Fetcher browser() {
		return require("BROWSER").cast();
	}

	/** {@return the Vectorize index named {@code VECTORIZE}} */
	default Vectorize vectorize() {
		return require("VECTORIZE").cast();
	}

	/**
	 * @param name the binding name
	 * @return the Vectorize index with that name
	 */
	default Vectorize vectorize(String name) {
		return require(name).cast();
	}

	/** {@return the Hyperdrive configuration named {@code HYPERDRIVE}} */
	default Hyperdrive hyperdrive() {
		return require("HYPERDRIVE").cast();
	}

	/**
	 * @param name the binding name
	 * @return the Hyperdrive configuration with that name
	 */
	default Hyperdrive hyperdrive(String name) {
		return require(name).cast();
	}

	/** {@return the Analytics Engine dataset named {@code ANALYTICS}} */
	default AnalyticsEngine analytics() {
		return require("ANALYTICS").cast();
	}

	/** {@return the email sending binding named {@code EMAIL}} */
	default EmailSender email() {
		return require("EMAIL").cast();
	}

	/**
	 * @param name the binding name
	 * @return the email sending binding with that name
	 */
	default EmailSender email(String name) {
		return require(name).cast();
	}

	/** {@return the Images binding named {@code IMAGES}} */
	default Images images() {
		return require("IMAGES").cast();
	}

	/** {@return the Workflow named {@code WORKFLOW}} */
	default Workflow workflow() {
		return require("WORKFLOW").cast();
	}

	/**
	 * @param name the binding name
	 * @return the Workflow with that name
	 */
	default Workflow workflow(String name) {
		return require(name).cast();
	}

	/** {@return the rate limiter named {@code RATELIMIT}} */
	default RateLimit rateLimit() {
		return require("RATELIMIT").cast();
	}

	/**
	 * @param name the binding name
	 * @return the rate limiter with that name
	 */
	default RateLimit rateLimit(String name) {
		return require(name).cast();
	}

	/** {@return the version metadata binding named {@code CF_VERSION_METADATA}} */
	default VersionMetadata version() {
		return require("CF_VERSION_METADATA").cast();
	}

	/** {@return the Secrets Store secret named {@code SECRETS}} */
	default SecretsStore secret() {
		return require("SECRETS").cast();
	}

	/**
	 * @param name the binding name
	 * @return the Secrets Store secret with that name
	 */
	default SecretsStore secret(String name) {
		return require(name).cast();
	}

	/** {@return the Pipeline named {@code PIPELINE}} */
	default Pipeline pipeline() {
		return require("PIPELINE").cast();
	}

	/** {@return the dispatch namespace named {@code DISPATCH}} */
	default DispatchNamespace dispatch() {
		return require("DISPATCH").cast();
	}

	/**
	 * @param name the binding name
	 * @return the container binding with that name
	 */
	default Container container(String name) {
		return require(name).cast();
	}

	/** {@return the AI Search binding named {@code AI_SEARCH}} */
	default AiSearch aiSearch() {
		return require("AI_SEARCH").cast();
	}

	/**
	 * @param name the binding name
	 * @return the AI Search binding with that name
	 */
	default AiSearch aiSearch(String name) {
		return require(name).cast();
	}

	/**
	 * Reads a binding, naming it when it is absent.
	 *
	 * <p>An undeclared binding is a configuration mistake rather than a runtime condition, and
	 * without this it surfaces as a null dereference somewhere further along.
	 *
	 * @param name the binding name
	 * @return the binding
	 */
	private Bound require(String name) {
		TSObject value = get(name);
		if (value == null || value.isNull()) {
			throw new IllegalStateException(
				"no binding named " + name + " is declared; add it to the bytebox bindings block"
			);
		}
		return new Bound(value);
	}

	/**
	 * A binding that is present, waiting to be typed.
	 *
	 * <p>The cast is unchecked because the runtime has no type to check against: a binding is an
	 * ordinary JavaScript object and its shape is whatever Cloudflare put there. Asking for the
	 * wrong type surfaces as a missing method on first use.
	 */
	record Bound(TSObject value) {
		@SuppressWarnings("unchecked")
		<T extends JSObject> T cast() {
			return (T) value;
		}
	}
}
