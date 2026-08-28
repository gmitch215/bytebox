package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;

/**
 * A Containers binding. Declared with {@code container()}, named {@code CONTAINER} by default.
 *
 * <p>Reached through a Durable Object, so an instance is addressed by id the same way and the
 * container's lifetime follows that instance.
 *
 * @since 1.0.0
 */
public interface Container extends JSObject {
	/** {@return whether the container is running} */
	@JSProperty
	boolean isRunning();

	/** Starts the container. */
	default void start() {
		Async.awaitVoid(doStart(null));
	}

	/**
	 * Starts the container with entrypoint overrides.
	 *
	 * @param options {@code entrypoint}, {@code env}, {@code enableInternet}
	 */
	default void start(TSObject options) {
		Async.awaitVoid(doStart(options));
	}

	/**
	 * Signals the container.
	 *
	 * @param signal the signal number, 15 for a graceful stop
	 */
	default void signal(int signal) {
		Async.awaitVoid(doSignal(signal));
	}

	/** Stops the container gracefully. */
	default void stop() {
		signal(15);
	}

	/**
	 * Opens a TCP connection to a port inside the container.
	 *
	 * @param port the port
	 * @return the socket
	 */
	TSObject getTcpPort(int port);

	@JSMethod("start")
	JSPromise<JSObject> doStart(TSObject options);

	@JSMethod("signal")
	JSPromise<JSObject> doSignal(int signal);
}
