package dev.gmitch215.bytebox.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.util.ArrayList;
import java.util.List;

/**
 * A zone's rules derived from the offsets it reports, rather than from a compiled rule table.
 *
 * <p>The platform already carries a timezone database, so shipping a second one inside the binary
 * would cost bytes to answer questions the isolate can already answer. What it exposes is one
 * question - the offset at an instant - and the rest of this class is the arithmetic that turns that
 * into the rest of the interface: which wall-clock readings exist, which happen twice, and when the
 * offset last changed.
 *
 * <p>Three limits, all consequences of deriving rather than reading:
 *
 * <ul>
 *   <li>{@link #nextTransition} and {@link #previousTransition} search a window of {@value #SEARCH_DAYS}
 *       days and answer {@code null} beyond it. Every zone that observes daylight saving changes twice
 *       a year, so a zone with nothing in that window has nothing scheduled.
 *   <li>{@link #getStandardOffset} is the smallest offset the zone reports across the year around the
 *       instant, which is the standard one because daylight saving only ever moves a clock forward.
 *   <li>{@link #getTransitions} and {@link #getTransitionRules} are the whole recorded history, and a
 *       search cannot produce a history. They refuse rather than answering an empty list, which is
 *       what a zone with no transitions at all would answer.
 * </ul>
 *
 * @since 1.0.0
 */
final class IntlZoneRules extends ZoneRules {

	/** How far {@link #nextTransition} and {@link #previousTransition} look. */
	static final int SEARCH_DAYS = 400;

	private static final long DAY = 86_400L;

	private final String zoneId;
	private final ZoneLookup lookup;

	IntlZoneRules(String zoneId, ZoneLookup lookup) {
		this.zoneId = zoneId;
		this.lookup = lookup;
	}

	@Override
	public boolean isFixedOffset() {
		return false;
	}

	@Override
	public ZoneOffset getOffset(Instant instant) {
		return offset(lookup.offsetAt(instant.getEpochSecond()));
	}

	@Override
	public ZoneOffset getOffset(LocalDateTime localDateTime) {
		List<ZoneOffset> valid = getValidOffsets(localDateTime);
		if (!valid.isEmpty()) return valid.get(0);
		// a reading in the gap has no offset, and the runtime answers with the one before the change
		return getTransition(localDateTime).getOffsetBefore();
	}

	/**
	 * Both sides of any nearby change are probed a day out, which yields every offset that could apply
	 * to this reading, and a candidate survives only if reading the clock back through it lands on
	 * itself. A gap leaves none standing and an overlap leaves two.
	 */
	@Override
	public List<ZoneOffset> getValidOffsets(LocalDateTime localDateTime) {
		long wall = localDateTime.toEpochSecond(ZoneOffset.UTC);
		int before = lookup.offsetAt(wall - DAY);
		int after = lookup.offsetAt(wall + DAY);

		List<ZoneOffset> valid = new ArrayList<>(2);
		int high = Math.max(before, after);
		int low = Math.min(before, after);
		if (lookup.offsetAt(wall - high) == high) valid.add(offset(high));
		if (low != high && lookup.offsetAt(wall - low) == low) valid.add(offset(low));
		return valid;
	}

	@Override
	public ZoneOffsetTransition getTransition(LocalDateTime localDateTime) {
		long wall = localDateTime.toEpochSecond(ZoneOffset.UTC);
		int before = lookup.offsetAt(wall - DAY);
		int after = lookup.offsetAt(wall + DAY);
		if (before == after) return null;
		if (getValidOffsets(localDateTime).size() == 1) return null;

		// the change is bracketed by reading the same wall clock through each of the two offsets
		long earliest = wall - Math.max(before, after);
		long latest = wall - Math.min(before, after);
		int target = lookup.offsetAt(latest);
		return transitionAt(bisect(earliest, latest, target, true), target);
	}

	@Override
	public ZoneOffset getStandardOffset(Instant instant) {
		return offset(standardSeconds(instant.getEpochSecond()));
	}

	@Override
	public Duration getDaylightSavings(Instant instant) {
		long at = instant.getEpochSecond();
		return Duration.ofSeconds(lookup.offsetAt(at) - standardSeconds(at));
	}

	@Override
	public boolean isDaylightSavings(Instant instant) {
		long at = instant.getEpochSecond();
		return lookup.offsetAt(at) != standardSeconds(at);
	}

	@Override
	public boolean isValidOffset(LocalDateTime localDateTime, ZoneOffset offset) {
		return offset != null && getValidOffsets(localDateTime).contains(offset);
	}

	@Override
	public ZoneOffsetTransition nextTransition(Instant instant) {
		long from = instant.getEpochSecond();
		int start = lookup.offsetAt(from);
		long previous = from;
		for (long probe = from + DAY; probe <= from + SEARCH_DAYS * DAY; probe += DAY) {
			int seen = lookup.offsetAt(probe);
			if (seen != start) {
				long at = bisect(previous, probe, start, false);
				return transitionAt(at, lookup.offsetAt(at));
			}
			previous = probe;
		}
		return null;
	}

	@Override
	public ZoneOffsetTransition previousTransition(Instant instant) {
		// the runtime rounds a sub-second reading up, so that a transition on the second counts as past
		long from = instant.getNano() > 0 ? instant.getEpochSecond() + 1 : instant.getEpochSecond();
		int start = lookup.offsetAt(from - 1);
		long next = from - 1;
		for (long probe = from - 1 - DAY; probe >= from - 1 - SEARCH_DAYS * DAY; probe -= DAY) {
			if (lookup.offsetAt(probe) != start) {
				return transitionAt(bisect(probe, next, start, true), start);
			}
			next = probe;
		}
		return null;
	}

	@Override
	public List<ZoneOffsetTransition> getTransitions() {
		throw new UnsupportedOperationException(
			"the recorded history of " +
				zoneId +
				" is not available here, because the offsets are read from the platform rather than from a" +
				" rule table; nextTransition and previousTransition answer from a search"
		);
	}

	@Override
	public List<ZoneOffsetTransitionRule> getTransitionRules() {
		throw new UnsupportedOperationException(
			"the recurring rules of " +
				zoneId +
				" are not available here, because the offsets are read from the platform rather than from a" +
				" rule table; nextTransition answers when the offset changes next"
		);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof IntlZoneRules && zoneId.equals(((IntlZoneRules) other).zoneId);
	}

	@Override
	public int hashCode() {
		return zoneId.hashCode();
	}

	@Override
	public String toString() {
		return "ZoneRules[" + zoneId + "]";
	}

	/** The last second still on the old offset, and the first on the new one, as a transition. */
	private ZoneOffsetTransition transitionAt(long at, int after) {
		int before = lookup.offsetAt(at - 1);
		ZoneOffset from = offset(before);
		return ZoneOffsetTransition.of(
			LocalDateTime.ofEpochSecond(at, 0, from),
			from,
			offset(after)
		);
	}

	/**
	 * The first second in {@code (low, high]} whose offset is, or is not, {@code compare}.
	 *
	 * <p>Both ends are known to disagree, so halving the interval always keeps one of each and ends on
	 * the second where they meet.
	 */
	private long bisect(long low, long high, int compare, boolean matching) {
		while (high - low > 1) {
			long middle = low + (high - low) / 2;
			if ((lookup.offsetAt(middle) == compare) == matching) high = middle;
			else low = middle;
		}
		return high;
	}

	/** The smallest offset across the year around an instant, which daylight saving cannot produce. */
	private int standardSeconds(long epochSecond) {
		int smallest = lookup.offsetAt(epochSecond);
		for (int month = -6; month <= 6; month++) {
			smallest = Math.min(smallest, lookup.offsetAt(epochSecond + month * 30L * DAY));
		}
		return smallest;
	}

	private static ZoneOffset offset(int totalSeconds) {
		return ZoneOffset.ofTotalSeconds(totalSeconds);
	}
}
