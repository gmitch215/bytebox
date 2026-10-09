package dev.gmitch215.bytebox.sync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a deadline")
class DeadlineTest {

	@Test
	@DisplayName("rounds a fraction of a millisecond up")
	void roundsUp() {
		long before = System.currentTimeMillis();
		long deadline = Deadline.after(1_500_000L);

		assertTrue(deadline >= before + 2);
		assertTrue(Deadline.left(deadline) <= 2);
	}

	@Test
	@DisplayName("has already passed for zero or a negative wait")
	void passedAlready() {
		assertTrue(Deadline.left(Deadline.after(0)) <= 0);
		assertTrue(Deadline.left(Deadline.after(-5_000_000L)) <= 0);
	}

	@Test
	@DisplayName("does not overflow for the longest wait")
	void longestWait() {
		assertTrue(Deadline.left(Deadline.after(Long.MAX_VALUE)) > 0);
	}
}
