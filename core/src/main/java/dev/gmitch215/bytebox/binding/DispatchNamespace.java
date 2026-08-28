package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSObject;

/**
 * A Workers for Platforms dispatch namespace. Declared with {@code dispatch()}, named
 * {@code DISPATCH} by default.
 *
 * <p>Reaches a user Worker in the namespace by name, so one Worker can route to many.
 *
 * @since 1.0.0
 */
public interface DispatchNamespace extends JSObject {
	/**
	 * Gets a handle on a user Worker. Does not check that it exists; a missing one fails on use.
	 *
	 * @param name the Worker's name in the namespace
	 * @return something to send requests to
	 */
	Fetcher get(String name);

	/**
	 * Gets a handle on a user Worker, with arguments and limits.
	 *
	 * @param name the Worker's name
	 * @param args passed to the user Worker
	 * @param options {@code limits}, {@code outbound}
	 * @return something to send requests to
	 */
	Fetcher get(String name, TSObject args, TSObject options);
}
