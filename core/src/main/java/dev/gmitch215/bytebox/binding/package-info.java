/**
 * The Cloudflare bindings, reached through {@link dev.gmitch215.bytebox.Env}.
 *
 * <p>Each binding is an interface over the JavaScript object Cloudflare provides, with its blocking
 * methods written as default methods over the promise-returning ones. So
 * {@code env.kv().get("key")} returns a value, and the promise underneath is not something a caller
 * has to see.
 *
 * <p>Names are assigned by the Gradle plugin and default to a type-derived value: {@code KV} for the
 * first KV namespace, {@code DB} for the first D1 database, {@code BLOB} for the first R2 bucket, and
 * a numeric suffix for each repeat. Every accessor on {@code Env} takes the default, and the
 * name-taking overload is for the repeats.
 *
 * <p>Anything here performs I/O, which Cloudflare Workers forbid outside a request context. Reading
 * a binding during module evaluation fails however it is spelled.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.binding;
