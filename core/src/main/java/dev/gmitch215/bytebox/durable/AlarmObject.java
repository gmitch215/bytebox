package dev.gmitch215.bytebox.durable;

import dev.gmitch215.bytebox.Env;

/**
 * A Durable Object that takes alarms.
 *
 * <p>The nearest thing to a scheduled task inside one object. Set the time with
 * {@link DurableState#setAlarm} and the handler runs then, whether or not anyone is connected.
 *
 * <p>Two things to build around. An invocation carries the same CPU limit as a request, so long work
 * has to re-arm rather than finish in one go. And delivery is at least once: a throw is retried, so a
 * handler that has already done half its work has to be safe to run again.
 *
 * @since 1.0.0
 */
public interface AlarmObject extends DurableObject {
	/**
	 * The alarm came due.
	 *
	 * @param state this instance's storage, alarm and sockets
	 * @param env the bindings
	 */
	void alarm(DurableState state, Env env);
}
