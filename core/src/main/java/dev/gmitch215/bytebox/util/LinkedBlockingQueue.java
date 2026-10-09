package dev.gmitch215.bytebox.util;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An unbounded queue whose blocking calls never wait.
 *
 * <p>One fiber runs at a time and nothing can add to the queue while a caller sleeps on it, so a
 * wait that could be satisfied is a wait that never ends. {@code take} on an empty queue therefore
 * throws, and {@code poll} with a timeout answers at once.
 *
 * @since 1.0.3
 */
public final class LinkedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

	private final ArrayDeque<E> items = new ArrayDeque<>();
	private final int capacity;

	public LinkedBlockingQueue() {
		this(Integer.MAX_VALUE);
	}

	public LinkedBlockingQueue(int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException();
		this.capacity = capacity;
	}

	public LinkedBlockingQueue(Collection<? extends E> initial) {
		this(Integer.MAX_VALUE);
		for (E e : initial) add(e);
	}

	@Override
	public boolean offer(E e) {
		if (e == null) throw new NullPointerException();
		if (items.size() >= capacity) return false;
		items.add(e);
		return true;
	}

	@Override
	public void put(E e) {
		if (!offer(e)) throw new IllegalStateException("queue is full and nothing can drain it");
	}

	@Override
	public boolean offer(E e, long timeout, TimeUnit unit) {
		return offer(e);
	}

	@Override
	public E poll() {
		return items.poll();
	}

	@Override
	public E take() {
		E e = items.poll();
		if (e == null) throw new IllegalStateException("queue is empty and nothing can fill it");
		return e;
	}

	@Override
	public E poll(long timeout, TimeUnit unit) {
		return items.poll();
	}

	@Override
	public E peek() {
		return items.peek();
	}

	@Override
	public int remainingCapacity() {
		return capacity - items.size();
	}

	@Override
	public int drainTo(Collection<? super E> c) {
		return drainTo(c, Integer.MAX_VALUE);
	}

	@Override
	public int drainTo(Collection<? super E> c, int max) {
		if (c == this) throw new IllegalArgumentException();
		int n = 0;
		while (n < max && !items.isEmpty()) {
			c.add(items.poll());
			n++;
		}
		return n;
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public Iterator<E> iterator() {
		return items.iterator();
	}
}
