package dev.gmitch215.bytebox.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The part of the generated Durable Object exports that is plain Java: one instance per identifier,
 * built on the caller. The exports themselves return promises and run in the workers lane.
 */
@DisplayName("building a Durable Object instance")
class DurableHandlersTest {

	@Test
	@DisplayName("builds once per identifier and hands the same instance back")
	void onePerIdentifier() {
		AtomicInteger made = new AtomicInteger();

		Object first = DurableHandlers.built("one-per-id/a", () -> {
			made.incrementAndGet();
			return new Object();
		});
		Object again = DurableHandlers.built("one-per-id/a", () -> {
			throw new AssertionError("built a second instance");
		});
		Object other = DurableHandlers.built("one-per-id/b", Object::new);

		assertSame(first, again);
		assertEquals(1, made.get());
		assertNotSame(first, other);
	}

	@Test
	@DisplayName("keeps nothing from a constructor that throws, so the next call builds again")
	void failedBuildLeavesNothing() {
		AtomicInteger attempts = new AtomicInteger();

		IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
			DurableHandlers.built("failed/a", () -> {
				attempts.incrementAndGet();
				throw new IllegalStateException("first attempt");
			})
		);
		Object retried = DurableHandlers.built("failed/a", () -> {
			attempts.incrementAndGet();
			return "second attempt";
		});

		assertEquals("first attempt", failure.getMessage());
		assertEquals("second attempt", retried);
		assertEquals(2, attempts.get());
		assertSame(retried, DurableHandlers.built("failed/a", () -> "never built"));
	}

	@Test
	@DisplayName("makes a second caller wait for a constructor that is still running")
	void secondCallerWaits() throws Exception {
		AtomicInteger made = new AtomicInteger();
		AtomicReference<Object> got = new AtomicReference<>();
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Thread first = new Thread(() ->
			DurableHandlers.built("waits/a", () -> {
				made.incrementAndGet();
				inside.countDown();
				await(release);
				return "built";
			})
		);
		first.start();
		assertTrue(inside.await(5, TimeUnit.SECONDS));
		Thread second = new Thread(() ->
			got.set(
				DurableHandlers.built("waits/a", () -> {
					made.incrementAndGet();
					return "built again";
				})
			)
		);
		second.start();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
			Thread.onSpinWait();
		}
		assertEquals(Thread.State.BLOCKED, second.getState());

		release.countDown();
		first.join(5000);
		second.join(5000);

		assertEquals("built", got.get());
		assertEquals(1, made.get());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch never released");
		} catch (InterruptedException e) {
			throw new AssertionError(e);
		}
	}
}
