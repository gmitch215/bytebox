package dev.gmitch215.bytebox.time;

import static dev.gmitch215.bytebox.time.Rules.derived;
import static dev.gmitch215.bytebox.time.Rules.real;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * When a zone's offset changed, and what a search cannot answer.
 *
 * <p>Both directions walk a day at a time over a bounded window and then halve the interval to the
 * second, which is a different claim from the one {@link ZoneOffsetTest} checks: not what the offset is
 * but when it last moved. The window is also what makes the recorded history unavailable, and that is
 * refused rather than answered with an empty list.
 */
@DisplayName("zone changes found by searching")
class ZoneSearchTest {

	// #region when the offset changed

	@ParameterizedTest
	@ValueSource(strings = { "America/New_York", "Europe/London", "Australia/Lord_Howe" })
	@DisplayName("finds the same next change from four points in the year")
	void findsTheSameNextChange(String zoneId) {
		IntlZoneRules ours = derived(zoneId);
		java.time.zone.ZoneRules theirs = real(zoneId);

		for (LocalDateTime from : List.of(
			LocalDateTime.of(2026, 1, 15, 0, 0),
			LocalDateTime.of(2026, 5, 15, 0, 0),
			LocalDateTime.of(2026, 8, 15, 0, 0),
			LocalDateTime.of(2026, 12, 15, 0, 0)
		)) {
			Instant at = from.toInstant(ZoneOffset.UTC);
			assertEquals(
				String.valueOf(theirs.nextTransition(at)),
				String.valueOf(ours.nextTransition(at)),
				zoneId + " next change after " + at
			);
			assertEquals(
				String.valueOf(theirs.previousTransition(at)),
				String.valueOf(ours.previousTransition(at)),
				zoneId + " previous change before " + at
			);
		}
	}

	@Test
	@DisplayName("counts a change on the second as past, the way the runtime does")
	void countsAChangeOnTheSecondAsPast() {
		IntlZoneRules ours = derived("America/New_York");
		java.time.zone.ZoneRules theirs = real("America/New_York");
		// the instant the clocks went forward in 2026, exactly
		Instant onTheSecond = LocalDateTime.of(2026, 3, 8, 7, 0).toInstant(ZoneOffset.UTC);

		assertEquals(
			String.valueOf(theirs.previousTransition(onTheSecond)),
			String.valueOf(ours.previousTransition(onTheSecond))
		);
		assertEquals(
			String.valueOf(theirs.nextTransition(onTheSecond)),
			String.valueOf(ours.nextTransition(onTheSecond))
		);
	}

	@Test
	@DisplayName("answers nothing for a zone that has scheduled nothing")
	void answersNothingForAFixedZone() {
		IntlZoneRules ours = derived("Asia/Kolkata");
		Instant at = Instant.ofEpochSecond(1_767_225_600L);

		assertNull(ours.nextTransition(at));
		assertNull(ours.previousTransition(at));
	}

	/**
	 * A zone that never changes has to stop rather than walk for ever, so what is counted is how many
	 * times the platform would be asked.
	 */
	@Test
	@DisplayName("a search for a change stops at the window rather than walking for ever")
	void aSearchStopsAtTheWindow() {
		java.time.zone.ZoneRules real = real("UTC");
		List<Long> asked = new ArrayList<>();
		IntlZoneRules ours = new IntlZoneRules("UTC", at -> {
			asked.add(at);
			return real.getOffset(Instant.ofEpochSecond(at)).getTotalSeconds();
		});

		assertNull(ours.nextTransition(Instant.EPOCH));
		int forward = asked.size();
		assertTrue(
			forward <= IntlZoneRules.SEARCH_DAYS + 2,
			"the search asked " + forward + " times"
		);

		asked.clear();
		assertNull(ours.previousTransition(Instant.EPOCH));
		assertTrue(asked.size() <= IntlZoneRules.SEARCH_DAYS + 2);
	}

	// #endregion

	// #region daylight saving

	@ParameterizedTest
	@ValueSource(strings = { "America/New_York", "Europe/London", "Asia/Kolkata" })
	@DisplayName("agrees on the standard offset and on how far the clock has moved")
	void agreesOnDaylightSaving(String zoneId) {
		IntlZoneRules ours = derived(zoneId);
		java.time.zone.ZoneRules theirs = real(zoneId);

		for (LocalDateTime moment : List.of(
			LocalDateTime.of(2026, 1, 15, 12, 0),
			LocalDateTime.of(2026, 7, 15, 12, 0)
		)) {
			Instant at = moment.toInstant(ZoneOffset.UTC);
			assertEquals(
				theirs.getStandardOffset(at),
				ours.getStandardOffset(at),
				zoneId + " standard offset at " + at
			);
			assertEquals(
				theirs.getDaylightSavings(at),
				ours.getDaylightSavings(at),
				zoneId + " saving at " + at
			);
			assertEquals(
				theirs.isDaylightSavings(at),
				ours.isDaylightSavings(at),
				zoneId + " in saving at " + at
			);
		}
	}

	// #endregion

	// #region what a search cannot answer

	@Test
	@DisplayName("refuses the recorded history rather than answering an empty one")
	void refusesTheHistory() {
		IntlZoneRules ours = derived("America/New_York");

		assertTrue(
			assertThrows(UnsupportedOperationException.class, ours::getTransitions)
				.getMessage()
				.contains("America/New_York")
		);
		assertTrue(
			assertThrows(UnsupportedOperationException.class, ours::getTransitionRules)
				.getMessage()
				.contains("America/New_York")
		);
	}

	@Test
	@DisplayName("is never a fixed offset, and names itself by its zone")
	void reportsItself() {
		IntlZoneRules ours = derived("Europe/London");

		assertFalse(ours.isFixedOffset());
		assertEquals("ZoneRules[Europe/London]", ours.toString());
		assertEquals(derived("Europe/London"), ours);
		assertEquals("Europe/London".hashCode(), ours.hashCode());
		assertFalse(ours.equals(derived("America/New_York")));
	}

	// #endregion

	// #region the fixed rules a bare offset has

	@Test
	@DisplayName("gives a bare offset the rules that never vary")
	void givesABareOffsetFixedRules() {
		ZoneOffset five = ZoneOffset.ofHours(-5);
		ZoneRules fixed = ZoneRules.of(five);
		Instant at = Instant.ofEpochSecond(1_767_225_600L);
		LocalDateTime reading = LocalDateTime.of(2026, 6, 1, 12, 0);

		assertTrue(fixed.isFixedOffset());
		assertEquals(five, fixed.getOffset(at));
		assertEquals(five, fixed.getOffset(reading));
		assertEquals(five, fixed.getStandardOffset(at));
		assertEquals(List.of(five), fixed.getValidOffsets(reading));
		assertNull(fixed.getTransition(reading));
		assertNull(fixed.nextTransition(at));
		assertNull(fixed.previousTransition(at));
		assertEquals(Duration.ZERO, fixed.getDaylightSavings(at));
		assertFalse(fixed.isDaylightSavings(at));
		assertTrue(fixed.isValidOffset(reading, five));
		assertFalse(fixed.isValidOffset(reading, ZoneOffset.UTC));
		assertTrue(fixed.getTransitions().isEmpty());
		assertTrue(fixed.getTransitionRules().isEmpty());
		assertEquals("FixedRules:-05:00", fixed.toString());
		assertEquals(ZoneRules.of(five), fixed);
		assertEquals(five.hashCode(), fixed.hashCode());
		assertFalse(fixed.equals(ZoneRules.of(ZoneOffset.UTC)));
	}

	@Test
	@DisplayName("refuses to assemble rules from a history it has no way to hold")
	void refusesAHistory() {
		assertThrows(NullPointerException.class, () -> ZoneRules.of(null));
		assertTrue(
			assertThrows(UnsupportedOperationException.class, () ->
				ZoneRules.of(ZoneOffset.UTC, ZoneOffset.UTC, List.of(), List.of(), List.of())
			)
				.getMessage()
				.contains("transition history")
		);
	}

	// #endregion
}
