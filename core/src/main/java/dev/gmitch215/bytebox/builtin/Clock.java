package dev.gmitch215.bytebox.builtin;

import org.teavm.jso.JSBody;

/**
 * Time, and what the platform does to it.
 *
 * <p>Cloudflare pins the clock between I/O operations, as a defence against timing attacks. Inside a
 * request that performs no I/O, {@code System.currentTimeMillis()} returns the same value however
 * many times it is called, and so does everything built on it. A loop waiting for the clock to move
 * never exits.
 *
 * <p>Two consequences worth knowing. Timing a section of code is not possible from inside the
 * isolate; {@code wrangler tail} reports the CPU time instead. And {@code Thread.sleep} only becomes
 * due because bytebox advances its own clock to meet it, which is why a sleep works at all.
 *
 * @since 1.0.0
 */
public final class Clock {

	private Clock() {}

	/**
	 * The current time, which advances only after I/O.
	 *
	 * @return milliseconds since the epoch
	 */
	@JSBody(script = "return Date.now();")
	public static native double now();

	/** {@return the current time as an ISO 8601 timestamp in UTC} */
	@JSBody(script = "return new Date().toISOString();")
	public static native String isoNow();

	/**
	 * Formats an instant as an ISO 8601 timestamp in UTC.
	 *
	 * @param millis milliseconds since the epoch
	 * @return the timestamp
	 */
	@JSBody(params = "millis", script = "return new Date(millis).toISOString();")
	public static native String iso(double millis);

	/**
	 * Parses an ISO 8601 timestamp.
	 *
	 * @param timestamp the timestamp
	 * @return milliseconds since the epoch, or NaN when it does not parse
	 */
	@JSBody(params = "timestamp", script = "return Date.parse(timestamp);")
	public static native double parse(String timestamp);

	/**
	 * Formats an instant for a timezone, using the platform's own zone data.
	 *
	 * @param millis milliseconds since the epoch
	 * @param timeZone an IANA zone, such as {@code America/New_York}
	 * @return the formatted instant
	 */
	@JSBody(
		params = { "millis", "timeZone" },
		script = "return new Date(millis).toLocaleString('en-US', { timeZone: timeZone });"
	)
	public static native String inZone(double millis, String timeZone);

	/**
	 * A zone's offset from UTC at an instant, which is what makes daylight saving observable.
	 *
	 * Example:
	 * {@code Clock.offsetMinutes(Clock.parse("2024-03-10T07:00:00Z"), "America/New_York")}
	 * returns {@code -300} because the offset is five hours west of UTC.
	 *
	 * @param millis milliseconds since the epoch
	 * @param timeZone an IANA zone
	 * @return the offset in minutes, positive east of UTC
	 */
	@JSBody(
		params = { "millis", "timeZone" },
		script = "const date = new Date(millis);" +
			" const local = new Date(date.toLocaleString('en-US', { timeZone: timeZone }));" +
			" const utc = new Date(date.toLocaleString('en-US', { timeZone: 'UTC' }));" +
			" return Math.round((local - utc) / 60000);"
	)
	public static native int offsetMinutes(double millis, String timeZone);

	/**
	 * A zone's offset from UTC at an instant, which is what makes daylight saving observable.
	 *
	 * Example:
	 * {@code Clock.offsetHours(Clock.parse("2024-03-10T07:00:00Z"), "America/New_York")}
	 * returns {@code -5} because the offset is five hours west of UTC.
	 *
	 * @param millis milliseconds since the epoch
	 * @param timeZone an IANA zone
	 * @return the offset in hours, positive east of UTC
	 */
	public static int offsetHours(double millis, String timeZone) {
		return offsetMinutes(millis, timeZone) / 60;
	}

	/**
	 * Reports whether the runtime knows a timezone.
	 *
	 * @param timeZone an IANA zone
	 * @return whether a formatter can be built for it
	 */
	@JSBody(
		params = "timeZone",
		script = "try { new Intl.DateTimeFormat('en', { timeZone: timeZone }); return true; }" +
			" catch (e) { return false; }"
	)
	public static native boolean supports(String timeZone);
}
