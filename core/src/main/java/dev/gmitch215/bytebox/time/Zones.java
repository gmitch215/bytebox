package dev.gmitch215.bytebox.time;

import org.teavm.jso.JSBody;

/**
 * The platform's timezone database, asked directly.
 *
 * <p>Separate from {@link ZoneRulesProvider} because that class is a substitute - the compiler renames
 * it to the one it stands in for - and a renamed class loses the annotations that turn a {@code native}
 * method into a call into JavaScript. So the provider is the substitute and this is where the calls
 * live.
 */
final class Zones {

	private Zones() {}

	/**
	 * The offset a zone was at an instant, in seconds.
	 *
	 * <p>Read as the difference between the wall clock the platform reports for the zone and the same
	 * instant in UTC, rather than from a named offset, because field formatting is the part of
	 * {@code Intl} every runtime has. The formatter is held per zone since building one is the
	 * expensive half.
	 */
	@JSBody(
		params = { "zoneId", "epochSecond" },
		script = "var cache = globalThis.__byteboxZones || (globalThis.__byteboxZones = new Map());" +
			"var format = cache.get(zoneId);" +
			"if (!format) {" +
			"  format = new Intl.DateTimeFormat('en-US', { timeZone: zoneId, hourCycle: 'h23'," +
			"    year: 'numeric', month: '2-digit', day: '2-digit'," +
			"    hour: '2-digit', minute: '2-digit', second: '2-digit' });" +
			"  cache.set(zoneId, format);" +
			"}" +
			"var millis = epochSecond * 1000;" +
			"var field = {};" +
			"var parts = format.formatToParts(new Date(millis));" +
			"for (var i = 0; i < parts.length; i++) field[parts[i].type] = parts[i].value;" +
			"var wall = new Date(0);" +
			"wall.setUTCFullYear(+field.year, +field.month - 1, +field.day);" +
			"wall.setUTCHours(+field.hour, +field.minute, +field.second, 0);" +
			"return Math.round((wall.getTime() - millis) / 1000);"
	)
	static native int offsetAt(String zoneId, double epochSecond);

	/** Whether the platform recognises an identifier, aliases included. */
	@JSBody(
		params = "zoneId",
		script = "try { new Intl.DateTimeFormat('en-US', { timeZone: zoneId }); return true; }" +
			" catch (e) { return false; }"
	)
	static native boolean recognises(String zoneId);

	/** Every identifier the platform names, comma separated. */
	@JSBody(
		script = "return typeof Intl.supportedValuesOf === 'function'" +
			" ? Intl.supportedValuesOf('timeZone').join(',') : 'UTC';"
	)
	static native String available();
}
