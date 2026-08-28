package dev.gmitch215.bytebox.durable;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.concurrent.Deferred;
import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.socket.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * What the generated Durable Object exports call.
 *
 * <p>The generated JavaScript class the runtime instantiates forwards each method here with the
 * instance's identifier, and each one starts the handler on a fiber and hands JavaScript a promise. A
 * handler cannot be called directly from a promise executor, because an executor cannot suspend and
 * every binding call does.
 *
 * <p>Not part of the surface a project writes against. It is public because generated code in another
 * package calls it.
 *
 * @since 1.0.0
 */
public final class DurableHandlers {

	/**
	 * One Java instance per Durable Object instance, keyed by identifier.
	 *
	 * <p>Entries live as long as the isolate. The platform hosts several instances of one class in one
	 * isolate sharing its memory, so this map's bound is the platform's own bound, and there is no
	 * eviction hook to remove an entry at.
	 */
	private static final Map<String, Object> INSTANCES = new HashMap<>();

	private DurableHandlers() {}

	/**
	 * The instance for an identifier, built on first use.
	 *
	 * @param identifier the Durable Object's identifier
	 * @param factory how to build one
	 * @param <T> the object's type
	 * @return the instance
	 */
	@SuppressWarnings("unchecked")
	public static <T> T instance(String identifier, Supplier<T> factory) {
		Object held = INSTANCES.get(identifier);
		if (held != null) return (T) held;
		T built = factory.get();
		INSTANCES.put(identifier, built);
		return built;
	}

	/**
	 * Runs a request.
	 *
	 * @param object the instance
	 * @param request the request
	 * @param state the instance's context
	 * @param env the bindings
	 * @return the response, once the handler has produced one
	 */
	public static JSPromise<Response> fetch(
		DurableObject object,
		Request request,
		DurableState state,
		Env env
	) {
		Deferred<Response> deferred = Deferred.create();
		start(deferred, () -> object.fetch(request, state, env));
		return deferred.promise();
	}

	/**
	 * Runs an alarm.
	 *
	 * @param object the instance
	 * @param state the instance's context
	 * @param env the bindings
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> alarm(AlarmObject object, DurableState state, Env env) {
		return complete(() -> object.alarm(state, env));
	}

	/**
	 * Runs a text message.
	 *
	 * @param object the instance
	 * @param socket which connection sent it
	 * @param text the message
	 * @param state the instance's context
	 * @param env the bindings
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> message(
		SocketObject object,
		WebSocket socket,
		String text,
		DurableState state,
		Env env
	) {
		return complete(() -> object.message(socket, text, state, env));
	}

	/**
	 * Runs a binary message.
	 *
	 * @param object the instance
	 * @param socket which connection sent it
	 * @param bytes the message
	 * @param state the instance's context
	 * @param env the bindings
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> message(
		SocketObject object,
		WebSocket socket,
		ArrayBuffer bytes,
		DurableState state,
		Env env
	) {
		return complete(() -> object.message(socket, Bytes.fromBuffer(bytes), state, env));
	}

	/**
	 * Runs a close.
	 *
	 * @param object the instance
	 * @param socket which connection closed
	 * @param code the close code
	 * @param reason the close reason
	 * @param clean whether the peer closed properly
	 * @param state the instance's context
	 * @param env the bindings
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> closed(
		SocketObject object,
		WebSocket socket,
		int code,
		String reason,
		boolean clean,
		DurableState state,
		Env env
	) {
		return complete(() -> object.closed(socket, code, reason, clean, state, env));
	}

	/**
	 * Runs a failure.
	 *
	 * @param object the instance
	 * @param socket which connection failed
	 * @param message what went wrong
	 * @param state the instance's context
	 * @param env the bindings
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> failed(
		SocketObject object,
		WebSocket socket,
		String message,
		DurableState state,
		Env env
	) {
		return complete(() -> object.failed(socket, message, state, env));
	}

	private static JSPromise<JSObject> complete(Runnable work) {
		Deferred<JSObject> deferred = Deferred.create();
		start(deferred, () -> {
			work.run();
			return null;
		});
		return deferred.promise();
	}

	private static <T extends JSObject> void start(Deferred<T> deferred, Supplier<T> work) {
		new Thread(() -> {
			try {
				deferred.resolve(work.get());
			} catch (Throwable failure) {
				deferred.reject(failure);
			}
		}).start();
	}
}
