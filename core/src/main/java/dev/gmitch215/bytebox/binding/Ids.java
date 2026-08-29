package dev.gmitch215.bytebox.binding;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** The one read on a Durable Object id the interop cannot describe as a property. */
final class Ids {

	private Ids() {}

	@JSBody(params = "id", script = "return typeof id.name === 'string' ? id.name : null;")
	static native String name(JSObject id);
}
