package dev.gmitch215.bytebox.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * {@code java.util.concurrent.locks.ReentrantLock}, which the class library does not carry at all.
 *
 * <p>This lock never waits. One fiber runs at a time, so an uncontended acquire is already exclusive
 * and needs nothing to make it so. Contention has exactly one cause here: a fiber suspended while
 * holding the lock and a second fiber reached the same critical section. That case is refused rather
 * than approximated, because the alternative is letting two fibers into a section written on the
 * assumption that one enters.
 *
 * <p>Blocking until the owner leaves would be the faithful answer, and it is not available: a
 * substituted class cannot contain a suspending call, and {@code Object.wait} is one. So the choice
 * is between refusing contention and ignoring it, and a library that guards a cache with a lock
 * never reaches either branch.
 *
 * @since 1.0.2
 */
public class ReentrantLock {

	private Thread owner;
	private int holds;

	/** A lock nothing holds. */
	public ReentrantLock() {}

	/**
	 * @param fair ignored; there is no queue to be fair about
	 */
	public ReentrantLock(boolean fair) {}

	/** Takes the lock, or refuses when another fiber is inside it. */
	public void lock() {
		Thread self = Thread.currentThread();
		if (owner != null && owner != self) throw contended();
		owner = self;
		holds++;
	}

	/**
	 * Takes the lock.
	 *
	 * @throws InterruptedException never, and declared so a caller compiled against the real class
	 *     links
	 */
	public void lockInterruptibly() throws InterruptedException {
		lock();
	}

	/** {@return whether the lock was free and has now been taken} */
	public boolean tryLock() {
		Thread self = Thread.currentThread();
		if (owner != null && owner != self) return false;
		owner = self;
		holds++;
		return true;
	}

	/**
	 * Takes the lock if it is free, without waiting for the time given.
	 *
	 * @param time ignored; waiting is what this lock cannot do
	 * @param unit ignored
	 * @return whether it was taken
	 * @throws InterruptedException never
	 */
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		return tryLock();
	}

	/** Releases the lock. */
	public void unlock() {
		if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
		if (--holds == 0) owner = null;
	}

	/** {@return never} */
	public Condition newCondition() {
		throw new UnsupportedOperationException(
			"a condition has to suspend the caller, which a substituted class cannot do here"
		);
	}

	/** {@return whether the calling fiber holds this lock} */
	public boolean isHeldByCurrentThread() {
		return owner == Thread.currentThread();
	}

	/** {@return whether any fiber holds this lock} */
	public boolean isLocked() {
		return owner != null;
	}

	/** {@return how many times the calling fiber has taken this lock without releasing it} */
	public int getHoldCount() {
		return owner == Thread.currentThread() ? holds : 0;
	}

	private static IllegalStateException contended() {
		return new IllegalStateException(
			"another fiber is inside this lock, which means one suspended while holding it;" +
				" this runtime cannot block the second fiber, so the section is not safe as written"
		);
	}
}
