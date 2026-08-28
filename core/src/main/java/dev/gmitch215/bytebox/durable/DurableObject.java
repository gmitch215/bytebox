package dev.gmitch215.bytebox.durable;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;

/**
 * A Durable Object written in Java.
 *
 * <p>Name the class with {@code durableObjects(...)} and the build writes the JavaScript class the
 * runtime instantiates, the binding, and the migration. One instance of this class exists per Durable
 * Object instance, so a field on it is that object's memory: strongly consistent, single-threaded, and
 * gone when the instance is evicted.
 *
 * {@snippet lang = "java":
 * public class Counter implements DurableObject {
 * 	private long cached = -1;
 *
 * 	@Override
 * 	public Response fetch(Request request, DurableState state, Env env) {
 * 		if (cached < 0) cached = state.getLong("count", 0);
 * 		state.put("count", ++cached);
 * 		return Bytebox.response(String.valueOf(cached));
 * 	}
 * }
 *}
 *
 * <p>A field is a cache, not the record. An instance can be evicted between requests, so anything that
 * has to survive goes through {@link DurableState}.
 *
 * <p>Implement {@link SocketObject} as well to take WebSockets, and {@link AlarmObject} to take alarms.
 * Which of the three a class implements decides what the generated JavaScript class exposes, so an
 * object that handles no alarm has no alarm handler at all.
 *
 * @since 1.0.0
 */
public interface DurableObject {
	/**
	 * Answers a request routed to this instance.
	 *
	 * @param request the request
	 * @param state this instance's storage, alarm and sockets
	 * @param env the bindings
	 * @return the response
	 */
	default Response fetch(Request request, DurableState state, Env env) {
		return Bytebox.response("this Durable Object does not handle requests", 405);
	}
}
