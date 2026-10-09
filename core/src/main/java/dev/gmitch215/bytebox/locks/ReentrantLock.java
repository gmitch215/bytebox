package dev.gmitch215.bytebox.locks;

import dev.gmitch215.bytebox.sync.Deadline;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * {@code java.util.concurrent.locks.ReentrantLock}, which the class library does not carry at all.
 *
 * <p>A second caller blocks until the owner leaves, the way it does on a JVM: the wait is
 * {@code Object.wait}, which suspends the calling fiber and lets the owner run. A call needs a fiber
 * under it: the generated entry point builds the handler, a Durable Object and a Workflow on one, so
 * their constructors and the static initializers they trigger may take the lock. A call made while no
 * fiber is running traps, whether or not it would have waited.
 *
 * <p>Fairness is not modelled: the argument is accepted and ignored, and waiters wake in whatever
 * order the monitor admits them.
 *
 * @since 1.0.2
 */
public class ReentrantLock implements Lock {

	private final Object monitor = new Object();
	private Thread owner;
	private int holds;

	/** A lock nothing holds. */
	public ReentrantLock() {}

	/**
	 * @param fair ignored; waiters are admitted in whatever order the monitor wakes them
	 */
	public ReentrantLock(boolean fair) {}

	/** Takes the lock, suspending the caller while another holds it. */
	@Override
	public void lock() {
		Thread self = Thread.currentThread();
		boolean interrupted = false;
		synchronized (monitor) {
			while (owner != null && owner != self) {
				try {
					monitor.wait();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			owner = self;
			holds++;
		}
		if (interrupted) self.interrupt();
	}

	/**
	 * Takes the lock, giving up if the caller is interrupted first.
	 *
	 * @throws InterruptedException if the caller was interrupted before or while waiting
	 */
	@Override
	public void lockInterruptibly() throws InterruptedException {
		if (Thread.interrupted()) throw new InterruptedException();
		Thread self = Thread.currentThread();
		synchronized (monitor) {
			while (owner != null && owner != self) monitor.wait();
			owner = self;
			holds++;
		}
	}

	/** {@return whether the lock was free and has now been taken} */
	@Override
	public boolean tryLock() {
		Thread self = Thread.currentThread();
		synchronized (monitor) {
			if (owner != null && owner != self) return false;
			owner = self;
			holds++;
			return true;
		}
	}

	/**
	 * Takes the lock, waiting up to the time given for its owner to leave.
	 *
	 * @param time the longest to wait
	 * @param unit the unit of {@code time}
	 * @return whether it was taken in time
	 * @throws InterruptedException if the caller was interrupted before or while waiting
	 */
	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		if (Thread.interrupted()) throw new InterruptedException();
		long deadline = Deadline.after(unit.toNanos(time));
		Thread self = Thread.currentThread();
		synchronized (monitor) {
			while (owner != null && owner != self) {
				long left = Deadline.left(deadline);
				if (left <= 0) return false;
				monitor.wait(left);
			}
			owner = self;
			holds++;
			return true;
		}
	}

	/** Releases the lock, and wakes the callers waiting for it once the last hold is gone. */
	@Override
	public void unlock() {
		synchronized (monitor) {
			if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
			if (--holds == 0) {
				owner = null;
				monitor.notifyAll();
			}
		}
	}

	/** {@return a condition bound to this lock} */
	@Override
	public Condition newCondition() {
		return new Waiters();
	}

	/** {@return whether the calling fiber holds this lock} */
	public boolean isHeldByCurrentThread() {
		synchronized (monitor) {
			return owner == Thread.currentThread();
		}
	}

	/** {@return whether any fiber holds this lock} */
	public boolean isLocked() {
		synchronized (monitor) {
			return owner != null;
		}
	}

	/** {@return how many times the calling fiber has taken this lock without releasing it} */
	public int getHoldCount() {
		synchronized (monitor) {
			return owner == Thread.currentThread() ? holds : 0;
		}
	}

	private final class Waiters implements Condition {

		private int waiting;
		private int signals;

		@Override
		public void await() throws InterruptedException {
			park(0, false, true);
		}

		@Override
		public void awaitUninterruptibly() {
			try {
				park(0, false, false);
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
		}

		@Override
		public long awaitNanos(long nanosTimeout) throws InterruptedException {
			return park(nanosTimeout, true, true);
		}

		@Override
		public boolean await(long time, TimeUnit unit) throws InterruptedException {
			return park(unit.toNanos(time), true, true) > 0;
		}

		@Override
		public boolean awaitUntil(Date deadline) throws InterruptedException {
			long millis = deadline.getTime() - System.currentTimeMillis();
			return park(TimeUnit.MILLISECONDS.toNanos(millis), true, true) > 0;
		}

		@Override
		public void signal() {
			synchronized (monitor) {
				if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
				if (signals < waiting) {
					signals++;
					monitor.notifyAll();
				}
			}
		}

		@Override
		public void signalAll() {
			synchronized (monitor) {
				if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
				signals = waiting;
				monitor.notifyAll();
			}
		}

		/** Releases every hold, waits for a signal, and takes the same holds back before returning. */
		private long park(long nanos, boolean timed, boolean interruptible)
			throws InterruptedException {
			Thread self = Thread.currentThread();
			if (interruptible && Thread.interrupted()) throw new InterruptedException();

			boolean interrupted = false;
			InterruptedException failure = null;
			long deadline = timed ? Deadline.after(nanos) : 0;
			long start = timed ? System.nanoTime() : 0;
			boolean signalled = false;
			synchronized (monitor) {
				if (owner != self) throw new IllegalMonitorStateException();
				int saved = holds;
				owner = null;
				holds = 0;
				monitor.notifyAll();
				waiting++;

				while (true) {
					if (signals > 0) {
						signals--;
						signalled = true;
						break;
					}
					try {
						if (timed) {
							long left = Deadline.left(deadline);
							if (left <= 0) break;
							monitor.wait(left);
						} else {
							monitor.wait();
						}
					} catch (InterruptedException e) {
						if (interruptible) {
							failure = e;
							break;
						}
						interrupted = true;
					}
				}
				waiting--;
				if (!signalled && signals > waiting) signals = waiting;

				while (owner != null) {
					try {
						monitor.wait();
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				owner = self;
				holds = saved;
			}

			if (interrupted) self.interrupt();
			if (failure != null) throw failure;
			if (!timed) return 0;
			// millis alone reads a same-tick signal as no time spent (nanoTime is frozen when virtual)
			long left = Math.min(
				Deadline.left(deadline) * 1_000_000L,
				nanos - (System.nanoTime() - start)
			);
			return signalled ? Math.max(1, left) : left;
		}
	}
}
