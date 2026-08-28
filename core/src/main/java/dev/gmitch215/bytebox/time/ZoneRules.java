package dev.gmitch215.bytebox.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.util.Collections;
import java.util.List;

/**
 * What a zone's offset was, and when it changed.
 *
 * <p>This is {@code java.time.zone.ZoneRules}, and it is here rather than taken from the
 * implementation underneath because that one can only be subclassed from inside its own package, and
 * the rules on this platform come from {@link IntlZoneRules} rather than from a rule table. Replacing
 * it also leaves the table reader, the rule builder and the binary format unreachable.
 *
 * <p>{@link #of(ZoneOffset)} builds the fixed rules a bare offset has, which is the case
 * {@code ZoneOffset.getRules()} needs. The five-argument form builds rules from a transition history
 * and refuses, because a history is what this platform does not have to give.
 *
 * @since 1.0.0
 */
public abstract class ZoneRules {

	/** For {@link IntlZoneRules} and the fixed rules below, and nothing else. */
	protected ZoneRules() {}

	/**
	 * The rules of a zone that is only ever one offset.
	 *
	 * @param offset the offset
	 * @return rules that always answer with it
	 */
	public static ZoneRules of(ZoneOffset offset) {
		if (offset == null) throw new NullPointerException("offset");
		return new Fixed(offset);
	}

	/**
	 * Rules assembled from a transition history, which is not available here.
	 *
	 * @param baseStandardOffset the standard offset before the history starts
	 * @param baseWallOffset the wall offset before the history starts
	 * @param standardOffsetTransitionList changes to the standard offset
	 * @param transitionList the transitions
	 * @param lastRules the recurring rules
	 * @return never
	 * @throws UnsupportedOperationException always
	 */
	public static ZoneRules of(
		ZoneOffset baseStandardOffset,
		ZoneOffset baseWallOffset,
		List<ZoneOffsetTransition> standardOffsetTransitionList,
		List<ZoneOffsetTransition> transitionList,
		List<ZoneOffsetTransitionRule> lastRules
	) {
		throw new UnsupportedOperationException(
			"rules cannot be assembled from a transition history here, because the offsets are read" +
				" from the platform rather than from a rule table; ZoneId.of names a zone the platform knows"
		);
	}

	/** {@return whether the offset never varies} */
	public abstract boolean isFixedOffset();

	/**
	 * The offset at an instant.
	 *
	 * @param instant the instant
	 * @return the offset
	 */
	public abstract ZoneOffset getOffset(Instant instant);

	/**
	 * The offset that applies to a wall-clock reading, preferring the earlier one where it happens
	 * twice and the one after the change where it does not happen at all.
	 *
	 * @param localDateTime the reading
	 * @return the offset
	 */
	public abstract ZoneOffset getOffset(LocalDateTime localDateTime);

	/**
	 * Every offset a wall-clock reading could mean: none in a gap, two in an overlap, otherwise one.
	 *
	 * @param localDateTime the reading
	 * @return the offsets, earliest first
	 */
	public abstract List<ZoneOffset> getValidOffsets(LocalDateTime localDateTime);

	/**
	 * The change a wall-clock reading falls inside.
	 *
	 * @param localDateTime the reading
	 * @return the change, or null when the reading is an ordinary one
	 */
	public abstract ZoneOffsetTransition getTransition(LocalDateTime localDateTime);

	/**
	 * The offset the zone keeps when daylight saving is not in force.
	 *
	 * @param instant the instant
	 * @return the offset
	 */
	public abstract ZoneOffset getStandardOffset(Instant instant);

	/**
	 * How far daylight saving has moved the clock.
	 *
	 * @param instant the instant
	 * @return the amount, zero when it is not in force
	 */
	public abstract Duration getDaylightSavings(Instant instant);

	/**
	 * Whether daylight saving is in force.
	 *
	 * @param instant the instant
	 * @return whether it is
	 */
	public abstract boolean isDaylightSavings(Instant instant);

	/**
	 * Whether a wall-clock reading and an offset go together.
	 *
	 * @param localDateTime the reading
	 * @param offset the offset
	 * @return whether they do
	 */
	public abstract boolean isValidOffset(LocalDateTime localDateTime, ZoneOffset offset);

	/**
	 * The next change after an instant.
	 *
	 * @param instant the instant
	 * @return the change, or null when none is scheduled
	 */
	public abstract ZoneOffsetTransition nextTransition(Instant instant);

	/**
	 * The last change before an instant.
	 *
	 * @param instant the instant
	 * @return the change, or null when there was none
	 */
	public abstract ZoneOffsetTransition previousTransition(Instant instant);

	/** {@return every recorded change} */
	public abstract List<ZoneOffsetTransition> getTransitions();

	/** {@return the rules by which changes recur} */
	public abstract List<ZoneOffsetTransitionRule> getTransitionRules();

	@Override
	public abstract boolean equals(Object otherRules);

	@Override
	public abstract int hashCode();

	/** A zone that is only ever one offset, which is what a bare offset amounts to. */
	private static final class Fixed extends ZoneRules {

		private final ZoneOffset offset;

		Fixed(ZoneOffset offset) {
			this.offset = offset;
		}

		@Override
		public boolean isFixedOffset() {
			return true;
		}

		@Override
		public ZoneOffset getOffset(Instant instant) {
			return offset;
		}

		@Override
		public ZoneOffset getOffset(LocalDateTime localDateTime) {
			return offset;
		}

		@Override
		public List<ZoneOffset> getValidOffsets(LocalDateTime localDateTime) {
			return Collections.singletonList(offset);
		}

		@Override
		public ZoneOffsetTransition getTransition(LocalDateTime localDateTime) {
			return null;
		}

		@Override
		public ZoneOffset getStandardOffset(Instant instant) {
			return offset;
		}

		@Override
		public Duration getDaylightSavings(Instant instant) {
			return Duration.ZERO;
		}

		@Override
		public boolean isDaylightSavings(Instant instant) {
			return false;
		}

		@Override
		public boolean isValidOffset(LocalDateTime localDateTime, ZoneOffset offset) {
			return this.offset.equals(offset);
		}

		@Override
		public ZoneOffsetTransition nextTransition(Instant instant) {
			return null;
		}

		@Override
		public ZoneOffsetTransition previousTransition(Instant instant) {
			return null;
		}

		@Override
		public List<ZoneOffsetTransition> getTransitions() {
			return Collections.emptyList();
		}

		@Override
		public List<ZoneOffsetTransitionRule> getTransitionRules() {
			return Collections.emptyList();
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Fixed && offset.equals(((Fixed) other).offset);
		}

		@Override
		public int hashCode() {
			return offset.hashCode();
		}

		@Override
		public String toString() {
			return "FixedRules:" + offset;
		}
	}
}
