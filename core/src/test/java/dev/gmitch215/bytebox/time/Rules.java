package dev.gmitch215.bytebox.time;

import java.time.Instant;
import java.time.ZoneId;

/** What both halves of the zone suite need: a real timezone database, seen one instant at a time. */
final class Rules {

	private Rules() {}

	/**
	 * The one question the platform answers, answered here by a real timezone database instead.
	 *
	 * <p>This is what makes the derivation checkable. The platform is only ever asked for the offset at
	 * an instant, so handing that same narrow view a JVM's own rules and comparing every derived answer
	 * against the JVM's direct one measures the arithmetic rather than the data.
	 */
	static IntlZoneRules derived(String zoneId) {
		java.time.zone.ZoneRules real = real(zoneId);
		return new IntlZoneRules(zoneId, at ->
			real.getOffset(Instant.ofEpochSecond(at)).getTotalSeconds()
		);
	}

	/** The JVM's own rules for a zone, which is what every comparison is against. */
	static java.time.zone.ZoneRules real(String zoneId) {
		return ZoneId.of(zoneId).getRules();
	}
}
