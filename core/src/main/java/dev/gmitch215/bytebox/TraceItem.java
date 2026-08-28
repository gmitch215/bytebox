package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * One trace event delivered to a {@link Tail} handler.
 *
 * @since 1.0.0
 */
public interface TraceItem extends JSObject {
	/** {@return the name of the Worker that produced the event}*/
	@JSProperty
	String getScriptName();

	/** {@return the invocation outcome, such as {@code ok} or {@code exception}} */
	@JSProperty
	String getOutcome();

	/** {@return CPU milliseconds the invocation consumed} */
	@JSProperty
	int getCpuTime();

	/** {@return wall-clock milliseconds the invocation took} */
	@JSProperty
	int getWallTime();
}
