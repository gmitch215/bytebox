package dev.gmitch215.bytebox.socket;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;

/**
 * The platform's own socket object, mirrored exactly.
 *
 * <p>{@link Socket} is what a caller uses. This is separate because a JSO interface is an overlay
 * type whose methods the compiler treats as final, so one cannot also implement
 * {@code AutoCloseable} — the compiler refuses it with "overriding final method of overlay types is
 * prohibited". The ergonomics therefore live in a class that wraps this rather than in the interface
 * itself.
 */
interface JSSocket extends JSObject {
	/** {@return the writable half} */
	@JSProperty
	JSObject getWritable();

	/** {@return the readable half} */
	@JSProperty
	JSObject getReadable();

	/** {@return a promise that settles when the connection closes} */
	@JSProperty
	JSPromise<JSObject> getClosed();

	/** {@return an encrypted socket, leaving this one unusable} */
	JSSocket startTLS();

	/** {@return a promise that settles once the connection is closed} */
	JSPromise<JSObject> close();
}
