package dev.gmitch215.bytebox.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the linked blocking queue substitute")
class LinkedBlockingQueueTest {

	@Test
	@DisplayName("keeps first-in first-out order and drains to a collection")
	void fifo() {
		LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
		queue.add("a");
		queue.add("b");
		queue.add("c");

		List<String> out = new ArrayList<>();
		assertEquals(2, queue.drainTo(out, 2));
		assertEquals(List.of("a", "b"), out);
		assertEquals(1, queue.drainTo(out));
		assertEquals(List.of("a", "b", "c"), out);
		assertTrue(queue.isEmpty());
	}

	@Test
	@DisplayName("answers an empty poll at once and refuses a take that could never return")
	void emptyQueue() throws Exception {
		LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

		assertNull(queue.poll());
		assertNull(queue.poll(5, TimeUnit.SECONDS));
		assertThrows(IllegalStateException.class, queue::take);
	}

	@Test
	@DisplayName("honours its capacity")
	void capacity() {
		LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(1);

		assertTrue(queue.offer("a"));
		assertFalse(queue.offer("b"));
		assertEquals(0, queue.remainingCapacity());
		assertThrows(IllegalStateException.class, () -> queue.put("b"));
		assertThrows(NullPointerException.class, () ->
			new LinkedBlockingQueue<String>().offer(null)
		);
		assertThrows(IllegalArgumentException.class, () -> new LinkedBlockingQueue<String>(0));
	}
}
