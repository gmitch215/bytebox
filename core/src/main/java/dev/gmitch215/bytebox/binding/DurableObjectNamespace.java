package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * A Durable Object binding. Declared with {@code durableObject()}, named {@code DO_} followed by
 * the class name.
 *
 * <p>One instance per id, in one location, with storage of its own. Two requests for the same id
 * reach the same instance, which is what makes a Durable Object the place to put state that has to
 * be consistent.
 *
 * {@snippet lang = "java":
 * DurableObjectStub counter = env.durableObject("DO_COUNTER").byName("global");
 * Response current = counter.fetch(request);
 *}
 *
 * @since 1.0.0
 */
public interface DurableObjectNamespace extends JSObject {
	/**
	 * The id for a name, which is stable: the same name always gives the same id.
	 *
	 * @param name the name
	 * @return the id
	 */
	DurableObjectId idFromName(String name);

	/**
	 * Parses an id previously produced by {@link DurableObjectId#toString()}.
	 *
	 * @param hex the id as hexadecimal
	 * @return the id
	 */
	DurableObjectId idFromString(String hex);

	/** {@return a fresh id nothing has used, placed near whoever asked for it} */
	DurableObjectId newUniqueId();

	/**
	 * The stub for an id, which is a handle rather than a round trip.
	 *
	 * @param id the id
	 * @return the stub
	 */
	DurableObjectStub get(DurableObjectId id);

	/**
	 * The stub for a name, in one step.
	 *
	 * @param name the name
	 * @return the stub
	 */
	default DurableObjectStub byName(String name) {
		return get(idFromName(name));
	}

	/**
	 * An instance's identity.
	 *
	 * @since 1.0.0
	 */
	interface DurableObjectId extends JSObject {
		/** {@return the id as hexadecimal, which {@link #idFromString(String)} reads back} */
		@JSMethod("toString")
		String asString();

		/** {@return the name this id came from, when it came from one} */
		@JSMethod("name")
		String name();
	}

	/**
	 * A handle on one instance.
	 *
	 * <p>Every call here crosses to wherever the instance lives, so it blocks. An instance's own
	 * methods are reachable through {@link #rpc(String, TSObject...)} when it extends Cloudflare's
	 * {@code DurableObject} base class.
	 *
	 * @since 1.0.0
	 */
	interface DurableObjectStub extends JSObject {
		/**
		 * Sends a request to the instance.
		 *
		 * @param request the request
		 * @return the instance's response
		 */
		default Response fetch(Request request) {
			return Async.await(send(request));
		}

		/**
		 * Sends a request to the instance by URL.
		 *
		 * @param url the URL, whose host is ignored
		 * @return the instance's response
		 */
		default Response fetch(String url) {
			return Async.await(send(url));
		}

		/**
		 * Calls one of the instance's own methods.
		 *
		 * @param method the method name
		 * @param args the arguments
		 * @return what the method returned
		 */
		default TSObject rpc(String method, TSObject... args) {
			return Async.await(Rpc.invoke(this, method, args));
		}

		/** {@return this instance's id} */
		@org.teavm.jso.JSProperty
		DurableObjectId getId();

		@JSMethod("fetch")
		JSPromise<Response> send(Request request);

		@JSMethod("fetch")
		JSPromise<Response> send(String url);
	}
}
