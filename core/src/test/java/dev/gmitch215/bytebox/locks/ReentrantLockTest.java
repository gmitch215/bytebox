package dev.gmitch215.bytebox.locks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("holding a lock")
class ReentrantLockTest {

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
	void refusesAForeignRelease() {
		assertThrows(IllegalMonitorStateException.class, () -> new ReentrantLock().unlock());
	}

	@Test
	@DisplayName("refuses a second caller rather than letting it into the section")
	void refusesContention() throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		lock.lock();

		AtomicReference<Throwable> refused = new AtomicReference<>();
		AtomicReference<Boolean> tried = new AtomicReference<>();
		Thread other = new Thread(() -> {
			tried.set(lock.tryLock());
			try {
				lock.lock();
			} catch (Throwable t) {
				refused.set(t);
			}
		});
		other.start();
		other.join();

		assertFalse(tried.get(), "tryLock must report the lock as taken");
		assertTrue(refused.get() instanceof IllegalStateException, String.valueOf(refused.get()));
		assertTrue(refused.get().getMessage().contains("suspended while holding it"));
		lock.unlock();
	}

	@Test
	@DisplayName("refuses a condition, which would have to suspend the caller")
	void refusesACondition() {
		assertThrows(UnsupportedOperationException.class, () -> new ReentrantLock().newCondition());
	}
}
