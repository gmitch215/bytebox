package dev.gmitch215.bytebox.js;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/**
 * Builds a JavaScript array from a Java one.
 *
 * <p>{@code JSArray.of} is the interop's own version of this and it does not survive being reached
 * from a new place: its body is generic, the compiler specialises it per call site, and past some
 * number of specialisations it emits {@code local.set expected type externref, found array.get} and
 * the module no longer compiles. The failure lands in whichever method called it, which is a long way
 * from the cause.
 *
 * <p>Nothing here is generic at the boundary. The two scripts take and return {@code JSObject}, so
 * there is one shape to compile however many element types call through.
 *
 * @since 1.0.0
 */
public final class JSArrays {

	private JSArrays() {}

	/**
	 * A JavaScript array holding the same values, in the same order.
	 *
	 * @param items the values
	 * @param <T> the element type
	 * @return the array
	 */
	public static <T extends JSObject> JSArray<T> of(T[] items) {
		JSObject array = empty();
		for (T item : items) push(array, item);
		return array.cast();
	}

	@JSBody(script = "return [];")
	private static native JSObject empty();

	@JSBody(params = { "array", "value" }, script = "array.push(value);")
	private static native void push(JSObject array, JSObject value);
}
