package dev.gmitch215.bytebox.locks;

import java.util.concurrent.TimeUnit;

/**
 * {@code java.util.concurrent.locks.Lock}, which the class library does not carry.
 *
 * @since 1.0.3
 */
public interface Lock {
	/** Takes the lock, suspending the caller until it is free. */
	void lock();

	/**
	 * Takes the lock, giving up if the caller is interrupted first.
	 *
	 * @throws InterruptedException if the caller was interrupted
	 */
	void lockInterruptibly() throws InterruptedException;

	/** {@return whether the lock was free and has now been taken} */
	boolean tryLock();

	/**
	 * @param time the longest to wait
	 * @param unit the unit of {@code time}
	 * @return whether the lock was taken in time
	 * @throws InterruptedException if the caller was interrupted
	 */
	boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

	/** Releases the lock. */
	void unlock();

	/** {@return a condition bound to this lock} */
	Condition newCondition();
}
