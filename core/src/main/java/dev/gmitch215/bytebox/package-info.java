/**
 * Worker entry points and the HTTP types a handler works in.
 *
 * <p>A Worker implements one interface per trigger it handles — {@link dev.gmitch215.bytebox.Worker}
 * for HTTP, {@link dev.gmitch215.bytebox.Scheduled} for a Cron Trigger, and so on. Implementing only
 * what you handle is what keeps an unhandled trigger out of the generated configuration and out of
 * the compiled binary.
 *
 * <p>Every handler looks synchronous and suspends underneath. A binding call returns a value rather
 * than a future because the compiler rewrites a blocking call into a continuation the host resumes.
 * There is no parallelism to go with it: Cloudflare Workers run one thread and do not provide the Web
 * Worker API, so a Java thread here is a fiber on the host's queue.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox;
