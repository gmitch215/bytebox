package dev.gmitch215.bytebox.sync;

/**
 * Timeouts measured on the clock a sleeping fiber is woken by.
 *
 * <p>{@code System.nanoTime} does not move across a sleep under the loader's default virtual time
 * policy, so a timed wait that counted with it never saw its time run out while another fiber's timer
 * was pending. {@code System.currentTimeMillis} moves with the scheduler under either policy.
 *
 * @since 1.0.3
 */
public final class Deadline {

	private Deadline() {}

	/**
	 * @param nanos how far ahead, in nanoseconds
	 * @return the wall-clock millisecond that is that far from now, rounded up
	 */
	public static long after(long nanos) {
		long millis = nanos / 1_000_000L + (nanos % 1_000_000L > 0 ? 1 : 0);
		return System.currentTimeMillis() + millis;
	}

	/**
	 * @param deadline a value from {@link #after}
	 * @return the milliseconds left, zero or negative once it has passed
	 */
	public static long left(long deadline) {
		return deadline - System.currentTimeMillis();
	}
}
