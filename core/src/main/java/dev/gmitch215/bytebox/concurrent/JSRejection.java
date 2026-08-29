package dev.gmitch215.bytebox.concurrent;

/**
 * A JavaScript promise rejected with something that is not a Java throwable.
 *
 * <p>Thrown by {@link Async#await(org.teavm.jso.core.JSPromise)} when a platform call fails: a
 * binding that refuses, a request that could not be sent, a stream that broke. The message is what
 * JavaScript rejected with, its stack when it had one.
 *
 * <p>Unchecked, because every binding on {@link dev.gmitch215.bytebox.Env} reads as a plain method
 * call and a checked exception on each of them would be ceremony rather than safety. A handler that
 * wants to survive a failing binding catches this; one that does not lets the invocation fail, which
 * is what the platform reports.
 *
 * @since 1.0.0
 */
public class JSRejection extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param message what the promise rejected with
	 */
	public JSRejection(String message) {
		super(message);
	}
}
