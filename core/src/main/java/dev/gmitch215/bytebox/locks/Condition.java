package dev.gmitch215.bytebox.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * {@code java.util.concurrent.locks.Condition}, which the class library does not carry.
 *
 * @since 1.0.3
 */
public interface Condition {
	/**
	 * Releases the lock and suspends until signalled or interrupted, then takes the lock back.
	 *
	 * @throws InterruptedException if the caller was interrupted
	 */
	void await() throws InterruptedException;

	/** Releases the lock and suspends until signalled, ignoring interruption. */
	void awaitUninterruptibly();

	/**
	 * @param nanosTimeout the longest to wait, in nanoseconds
	 * @return an estimate of the nanoseconds left, zero or negative when the wait timed out
	 * @throws InterruptedException if the caller was interrupted
	 */
	long awaitNanos(long nanosTimeout) throws InterruptedException;

	/**
	 * @param time the longest to wait
	 * @param unit the unit of {@code time}
	 * @return false when the wait timed out
	 * @throws InterruptedException if the caller was interrupted
	 */
	boolean await(long time, TimeUnit unit) throws InterruptedException;

	/**
	 * @param deadline the moment to stop waiting at
	 * @return false when the deadline passed
	 * @throws InterruptedException if the caller was interrupted
	 */
	boolean awaitUntil(Date deadline) throws InterruptedException;

	/** Wakes one waiter. */
	void signal();

	/** Wakes every waiter. */
	void signalAll();
}
