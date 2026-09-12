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
 * <p>Final, and a zone is carried in a field rather than in a subclass. A subclass would have to name
 * this class as its supertype, which is the one name a substitute may not use: the compiler
 * translates {@code java.time.zone.ZoneRules} into a copy of this class under the original name,
 * while a subclass goes on extending this one, and the two are unrelated wasm types. The symptom is
 * a {@code CompileError} from the engine naming a method that returns one where the other is
 * declared, so javac refusing the subclass is the cheaper failure.
 *
 * @since 1.0.0
 */
public final class ZoneRules {

	/** Set when the rules are a bare offset, and null when {@link #zone} carries them instead. */
	private final ZoneOffset fixed;

	private final IntlZoneRules zone;

	private ZoneRules(ZoneOffset fixed, IntlZoneRules zone) {
		this.fixed = fixed;
		this.zone = zone;
	}

	/**
	 * The rules of a zone that is only ever one offset.
	 *
	 * @param offset the offset
	 * @return rules that always answer with it
	 */
	public static ZoneRules of(ZoneOffset offset) {
		if (offset == null) throw new NullPointerException("offset");
		return new ZoneRules(offset, null);
	}

	/**
	 * The rules of a zone the platform knows, derived from the offsets it reports.
	 *
	 * <p>Not an {@code of} overload: this class mirrors {@code java.time.zone.ZoneRules}, which has
	 * only the two above, and a third would make {@code of(null)} ambiguous.
	 *
	 * @param zone the derivation
	 * @return rules that ask it
	 */
	static ZoneRules forZone(IntlZoneRules zone) {
		return new ZoneRules(null, zone);
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
	public boolean isFixedOffset() {
		return fixed != null;
	}

	/**
	 * The offset at an instant.
	 *
	 * @param instant the instant
	 * @return the offset
	 */
	public ZoneOffset getOffset(Instant instant) {
		return fixed != null ? fixed : zone.getOffset(instant);
	}

	/**
	 * The offset that applies to a wall-clock reading, preferring the earlier one where it happens
	 * twice and the one after the change where it does not happen at all.
	 *
	 * @param localDateTime the reading
	 * @return the offset
	 */
	public ZoneOffset getOffset(LocalDateTime localDateTime) {
		return fixed != null ? fixed : zone.getOffset(localDateTime);
	}

	/**
	 * Every offset a wall-clock reading could mean: none in a gap, two in an overlap, otherwise one.
	 *
	 * @param localDateTime the reading
	 * @return the offsets, earliest first
	 */
	public List<ZoneOffset> getValidOffsets(LocalDateTime localDateTime) {
		return fixed != null
			? Collections.singletonList(fixed)
			: zone.getValidOffsets(localDateTime);
	}

	/**
	 * The change a wall-clock reading falls inside.
	 *
	 * @param localDateTime the reading
	 * @return the change, or null when the reading is an ordinary one
	 */
	public ZoneOffsetTransition getTransition(LocalDateTime localDateTime) {
		return fixed != null ? null : zone.getTransition(localDateTime);
	}

	/**
	 * The offset the zone keeps when daylight saving is not in force.
	 *
	 * @param instant the instant
	 * @return the offset
	 */
	public ZoneOffset getStandardOffset(Instant instant) {
		return fixed != null ? fixed : zone.getStandardOffset(instant);
	}

	/**
	 * How far daylight saving has moved the clock.
	 *
	 * @param instant the instant
	 * @return the amount, zero when it is not in force
	 */
	public Duration getDaylightSavings(Instant instant) {
		return fixed != null ? Duration.ZERO : zone.getDaylightSavings(instant);
	}

	/**
	 * Whether daylight saving is in force.
	 *
	 * @param instant the instant
	 * @return whether it is
	 */
	public boolean isDaylightSavings(Instant instant) {
		return fixed != null ? false : zone.isDaylightSavings(instant);
	}

	/**
	 * Whether a wall-clock reading and an offset go together.
	 *
	 * @param localDateTime the reading
	 * @param offset the offset
	 * @return whether they do
	 */
	public boolean isValidOffset(LocalDateTime localDateTime, ZoneOffset offset) {
		return fixed != null ? fixed.equals(offset) : zone.isValidOffset(localDateTime, offset);
	}

	/**
	 * The next change after an instant.
	 *
	 * @param instant the instant
	 * @return the change, or null when none is scheduled
	 */
	public ZoneOffsetTransition nextTransition(Instant instant) {
		return fixed != null ? null : zone.nextTransition(instant);
	}

	/**
	 * The last change before an instant.
	 *
	 * @param instant the instant
	 * @return the change, or null when there was none
	 */
	public ZoneOffsetTransition previousTransition(Instant instant) {
		return fixed != null ? null : zone.previousTransition(instant);
	}

	/** {@return every recorded change} */
	public List<ZoneOffsetTransition> getTransitions() {
		return fixed != null ? Collections.emptyList() : zone.getTransitions();
	}

	/** {@return the rules by which changes recur} */
	public List<ZoneOffsetTransitionRule> getTransitionRules() {
		return fixed != null ? Collections.emptyList() : zone.getTransitionRules();
	}

	@Override
	public boolean equals(Object otherRules) {
		if (!(otherRules instanceof ZoneRules)) return false;
		ZoneRules other = (ZoneRules) otherRules;
		return fixed != null ? fixed.equals(other.fixed) : zone.equals(other.zone);
	}

	@Override
	public int hashCode() {
		return fixed != null ? fixed.hashCode() : zone.hashCode();
	}

	@Override
	public String toString() {
		return fixed != null ? "FixedRules:" + fixed : zone.toString();
	}
}
