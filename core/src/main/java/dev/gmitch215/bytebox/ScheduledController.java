package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * The controller Cloudflare passes to a {@code scheduled} handler.
 *
 * <p>{@link Cron} is what a handler sees. This is the JavaScript object it is read from.
 *
 * @since 1.0.0
 */
public interface ScheduledController extends JSObject {
	/** {@return the cron expression that fired} */
	@JSProperty
	String getCron();

	/** {@return when the invocation was due, in milliseconds since the epoch} */
	@JSProperty
	double getScheduledTime();

	/** {@return the trigger type, such as {@code scheduled}} */
	@JSProperty
	String getType();
}
