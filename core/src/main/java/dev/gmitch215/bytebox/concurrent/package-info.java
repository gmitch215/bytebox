/**
 * Suspension, and the pieces that make a blocking API possible.
 *
 * <p>{@link dev.gmitch215.bytebox.concurrent.Async#await} turns a JavaScript promise into an ordinary
 * blocking call. {@link dev.gmitch215.bytebox.concurrent.Deferred} does the reverse, and is what every
 * trigger's entry point is built from: a promise the host can await, settled by Java once the handler
 * has genuinely finished. {@link dev.gmitch215.bytebox.concurrent.Future} carries the part of
 * {@code CompletableFuture} that means anything here, since the class library has no {@code Future}
 * at all.
 *
 * <p>Suspension is real; parallelism is not. Two fibers never run at once, so a
 * {@code synchronized} block is uncontended and a thread pool cannot exist. What suspension does buy
 * is that a handler can park on I/O without blocking the isolate — and that a second request can
 * arrive while it is parked, which is why entry into a module is serialised.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.concurrent;
