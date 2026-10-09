package dev.gmitch215.bytebox.locks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("holding a lock")
@Timeout(10)
class ReentrantLockTest {

	private static void awaitWaiting(Thread thread) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (
			thread.getState() != Thread.State.WAITING &&
			thread.getState() != Thread.State.TIMED_WAITING
		) {
			if (System.nanoTime() > deadline) throw new AssertionError(
				"never blocked: " + thread.getState()
			);
			Thread.sleep(1);
		}
	}

	@Test
	@DisplayName("counts the times one caller took it, and frees it on the last release")
	void reentrant() {
		ReentrantLock lock = new ReentrantLock();

		assertFalse(lock.isLocked());
		lock.lock();
		lock.lock();
		assertEquals(2, lock.getHoldCount());
		assertTrue(lock.isHeldByCurrentThread());

		lock.unlock();
		assertTrue(lock.isLocked());
		lock.unlock();
		assertFalse(lock.isLocked());
		assertEquals(0, lock.getHoldCount());
	}

	@Test
	@DisplayName("tryLock takes a free lock and re-takes one the caller already holds")
	void tryLock() {
		ReentrantLock lock = new ReentrantLock();

		assertTrue(lock.tryLock());
		assertTrue(lock.tryLock());
		assertEquals(2, lock.getHoldCount());
		lock.unlock();
		lock.unlock();
	}

	@Test
	@DisplayName("refuses a release by a caller that does not hold it")
	void refusesAForeignRelease() throws InterruptedException {
		assertThrows(IllegalMonitorStateException.class, () -> new ReentrantLock().unlock());

		ReentrantLock lock = new ReentrantLock();
		lock.lock();
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread other = new Thread(() -> {
			try {
				lock.unlock();
			} catch (Throwable t) {
				thrown.set(t);
			}
		});
		other.start();
		other.join();
		assertTrue(
			thrown.get() instanceof IllegalMonitorStateException,
			String.valueOf(thrown.get())
		);
		assertTrue(lock.isHeldByCurrentThread());
		lock.unlock();
	}

	@Test
	@DisplayName("holds a second caller in lock() until the owner releases")
	void blocksUntilReleased() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		lock.lock();

		AtomicBoolean tried = new AtomicBoolean();
		AtomicBoolean acquired = new AtomicBoolean();
		Thread other = new Thread(() -> {
			tried.set(lock.tryLock());
			lock.lock();
			acquired.set(true);
			lock.unlock();
		});
		other.start();
		awaitWaiting(other);

		assertFalse(tried.get(), "tryLock must report the lock as taken");
		assertFalse(acquired.get(), "the second caller got in while the owner held the lock");
		lock.unlock();
		other.join();
		assertTrue(acquired.get());
		assertFalse(lock.isLocked());
	}

	@Test
	@DisplayName("lets one caller into the section at a time")
	void mutualExclusion() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		AtomicInteger inside = new AtomicInteger();
		AtomicInteger worst = new AtomicInteger();
		int[] counter = { 0 };

		List<Thread> threads = new ArrayList<>();
		for (int t = 0; t < 4; t++) {
			Thread thread = new Thread(() -> {
				for (int i = 0; i < 500; i++) {
					lock.lock();
					try {
						worst.accumulateAndGet(inside.incrementAndGet(), Math::max);
						counter[0]++;
						inside.decrementAndGet();
					} finally {
						lock.unlock();
					}
				}
			});
			threads.add(thread);
			thread.start();
		}
		for (Thread thread : threads) thread.join();

		assertEquals(1, worst.get());
		assertEquals(2000, counter[0]);
	}

	@Test
	@DisplayName(
		"gives up a timed tryLock when the owner does not leave, and takes it when it does"
	)
	void timedTryLock() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		lock.lock();

		AtomicBoolean early = new AtomicBoolean(true);
		AtomicBoolean late = new AtomicBoolean();
		AtomicBoolean laterTaken = new AtomicBoolean();
		Thread other = new Thread(() -> {
			try {
				early.set(lock.tryLock(30, TimeUnit.MILLISECONDS));
				late.set(lock.tryLock(5, TimeUnit.SECONDS));
				if (late.get()) lock.unlock();
			} catch (InterruptedException e) {
				laterTaken.set(true);
			}
		});
		other.start();
		awaitWaiting(other);
		Thread.sleep(80);
		lock.unlock();
		other.join();

		assertFalse(early.get(), "a 30 ms wait on a held lock must report false");
		assertTrue(late.get(), "a wait that outlasts the owner must take the lock");
		assertFalse(laterTaken.get());
	}

	@Test
	@DisplayName(
		"stops waiting in lockInterruptibly when interrupted, and keeps the flag in lock()"
	)
	void interruption() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		lock.lock();

		AtomicReference<Throwable> interrupted = new AtomicReference<>();
		Thread polite = new Thread(() -> {
			try {
				lock.lockInterruptibly();
			} catch (InterruptedException e) {
				interrupted.set(e);
			}
		});
		polite.start();
		awaitWaiting(polite);
		polite.interrupt();
		polite.join();
		assertTrue(interrupted.get() instanceof InterruptedException);
		assertEquals(1, lock.getHoldCount());

		AtomicBoolean flagKept = new AtomicBoolean();
		Thread stubborn = new Thread(() -> {
			lock.lock();
			flagKept.set(Thread.currentThread().isInterrupted());
			lock.unlock();
		});
		stubborn.start();
		awaitWaiting(stubborn);
		stubborn.interrupt();
		lock.unlock();
		stubborn.join();
		assertTrue(flagKept.get(), "lock() must hand the interrupt back once it has the lock");
	}

	@Test
	@DisplayName(
		"hands a signal from one caller to a waiting one, which wakes holding the lock again"
	)
	void conditionHandshake() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		Condition ready = lock.newCondition();
		boolean[] flag = { false };
		AtomicInteger holdsAfter = new AtomicInteger();

		Thread waiter = new Thread(() -> {
			lock.lock();
			lock.lock();
			try {
				while (!flag[0]) ready.await();
				holdsAfter.set(lock.getHoldCount());
			} catch (InterruptedException e) {
				holdsAfter.set(-1);
			} finally {
				lock.unlock();
				lock.unlock();
			}
		});
		waiter.start();
		awaitWaiting(waiter);

		lock.lock();
		assertEquals(
			1,
			lock.getHoldCount(),
			"the waiter released its holds, so this caller is first in"
		);
		flag[0] = true;
		ready.signal();
		lock.unlock();
		waiter.join();

		assertEquals(2, holdsAfter.get(), "await must give back the holds it released");
		assertFalse(lock.isLocked());
	}

	@Test
	@DisplayName("wakes every waiter on signalAll and one on signal")
	void signalAndSignalAll() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		Condition gate = lock.newCondition();
		AtomicInteger woke = new AtomicInteger();

		List<Thread> waiters = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Thread t = new Thread(() -> {
				lock.lock();
				try {
					gate.await();
					woke.incrementAndGet();
				} catch (InterruptedException e) {
					woke.addAndGet(100);
				} finally {
					lock.unlock();
				}
			});
			waiters.add(t);
			t.start();
		}
		for (Thread t : waiters) awaitWaiting(t);

		lock.lock();
		gate.signal();
		lock.unlock();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (woke.get() < 1 && System.nanoTime() < deadline) Thread.sleep(1);
		Thread.sleep(50);
		assertEquals(1, woke.get(), "signal wakes one waiter, not all");

		lock.lock();
		gate.signalAll();
		lock.unlock();
		for (Thread t : waiters) t.join();
		assertEquals(3, woke.get());
	}

	@Test
	@DisplayName("times a wait out and reports it")
	void timedAwait() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		Condition never = lock.newCondition();

		lock.lock();
		try {
			assertFalse(never.await(20, TimeUnit.MILLISECONDS));
			assertTrue(never.awaitNanos(TimeUnit.MILLISECONDS.toNanos(20)) <= 0);
			assertFalse(never.awaitUntil(new Date(System.currentTimeMillis() + 20)));
			assertTrue(
				lock.isHeldByCurrentThread(),
				"a timed out await still returns holding the lock"
			);
			assertEquals(1, lock.getHoldCount());
		} finally {
			lock.unlock();
		}
	}

	@Test
	@DisplayName("lets awaitNanos report time left when a signal arrives first")
	void awaitNanosSignalled() throws InterruptedException {
		long timeout = TimeUnit.SECONDS.toNanos(5);
		for (int i = 0; i < 200; i++) {
			ReentrantLock lock = new ReentrantLock();
			Condition c = lock.newCondition();
			AtomicReference<Long> left = new AtomicReference<>();

			Thread waiter = new Thread(() -> {
				lock.lock();
				try {
					left.set(c.awaitNanos(timeout));
				} catch (InterruptedException e) {
					left.set(Long.MIN_VALUE);
				} finally {
					lock.unlock();
				}
			});
			waiter.start();
			while (waiter.isAlive() && waiter.getState() != Thread.State.TIMED_WAITING)
				Thread.yield();
			lock.lock();
			c.signal();
			lock.unlock();
			waiter.join();

			assertTrue(left.get() > 0 && left.get() < timeout, "round " + i + ": " + left.get());
		}
	}

	@Test
	@DisplayName("refuses await and signal from a caller that does not hold the lock")
	void conditionNeedsTheLock() {
		ReentrantLock lock = new ReentrantLock();
		Condition c = lock.newCondition();

		assertThrows(IllegalMonitorStateException.class, c::await);
		assertThrows(IllegalMonitorStateException.class, c::awaitUninterruptibly);
		assertThrows(IllegalMonitorStateException.class, () -> c.awaitNanos(1));
		assertThrows(IllegalMonitorStateException.class, () -> c.await(1, TimeUnit.NANOSECONDS));
		assertThrows(IllegalMonitorStateException.class, () -> c.awaitUntil(new Date()));
		assertThrows(IllegalMonitorStateException.class, c::signal);
		assertThrows(IllegalMonitorStateException.class, c::signalAll);
	}

	@Test
	@DisplayName("is usable through the Lock interface")
	void throughTheInterface() {
		Lock lock = new ReentrantLock(true);
		lock.lock();
		assertTrue(lock.tryLock());
		lock.unlock();
		lock.unlock();
	}
}
