package dev.gmitch215.bytebox.time;

/**
 * The offset a zone was at an instant.
 *
 * <p>One function is the whole of what a timezone database is asked, and everything else about a zone
 * can be derived from it: which wall-clock times exist, when the offset last changed, whether daylight
 * saving is in force. {@link IntlZoneRules} does that deriving, and takes this so the derivation can be
 * checked against a real timezone database on a JVM rather than only against the platform's.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ZoneLookup {
	/**
	 * The offset from UTC at an instant.
	 *
	 * @param epochSecond seconds since the epoch
	 * @return the offset in seconds, positive east of Greenwich
	 */
	int offsetAt(long epochSecond);
}
