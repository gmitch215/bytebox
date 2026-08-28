package dev.gmitch215.bytebox.time;

import static dev.gmitch215.bytebox.time.Rules.derived;
import static dev.gmitch215.bytebox.time.Rules.real;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What offset applies, and to which wall-clock readings.
 *
 * <p>On the platform the only thing known about a zone is the offset it reports at an instant, and
 * everything else is worked out from that. This lane runs on a JVM, which has the rules the platform
 * is only being asked about one instant at a time, so the working out is checkable rather than
 * assertable. When a change happens and what a search can and cannot answer is {@link ZoneSearchTest}.
 */
@DisplayName("zone offsets derived from one lookup")
class ZoneOffsetTest {

	// #region the offset at an instant

	@ParameterizedTest
	@ValueSource(
		strings = {
			"America/New_York",
			"Europe/London",
			"Australia/Lord_Howe",
			"Asia/Kolkata",
			"Pacific/Apia",
			"UTC"
		}
	)
	@DisplayName("agrees on the offset every three hours across two years")
	void agreesOnTheOffsetAcrossTwoYears(String zoneId) {
		IntlZoneRules ours = derived(zoneId);
		java.time.zone.ZoneRules theirs = real(zoneId);
		for (long at = 1_735_689_600L; at < 1_798_761_600L; at += 10_800L) {
			Instant instant = Instant.ofEpochSecond(at);
			assertEquals(
				theirs.getOffset(instant),
				ours.getOffset(instant),
				zoneId + " at " + instant
			);
		}
	}

	@Test
	@DisplayName("agrees on an instant far from now, in both directions")
	void agreesFarFromNow() {
		IntlZoneRules ours = derived("Europe/London");
		java.time.zone.ZoneRules theirs = real("Europe/London");

		for (long at : new long[] { 0L, -2_208_988_800L, 4_102_444_800L, 253_402_300_799L }) {
			Instant instant = Instant.ofEpochSecond(at);
			assertEquals(theirs.getOffset(instant), ours.getOffset(instant), "at " + instant);
		}
	}

	// #endregion

	// #region wall-clock readings

	/**
	 * Every minute of the two days a zone changes its clocks, which is where a gap and an overlap both
	 * are, and where every reading that has no offset or two of them lives.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "America/New_York", "Europe/London", "Australia/Lord_Howe" })
	@DisplayName("agrees on which wall-clock readings exist and what they mean")
	void agreesOnWallClockReadings(String zoneId) {
		IntlZoneRules ours = derived(zoneId);
		java.time.zone.ZoneRules theirs = real(zoneId);

		for (LocalDateTime day : List.of(
			LocalDateTime.of(2026, 3, 8, 0, 0),
			LocalDateTime.of(2026, 11, 1, 0, 0),
			LocalDateTime.of(2026, 3, 29, 0, 0),
			LocalDateTime.of(2026, 10, 25, 0, 0),
			LocalDateTime.of(2026, 4, 5, 0, 0)
		)) {
			for (int minute = 0; minute < 24 * 60; minute++) {
				LocalDateTime reading = day.plusMinutes(minute);
				assertEquals(
					theirs.getValidOffsets(reading),
					ours.getValidOffsets(reading),
					zoneId + " valid offsets at " + reading
				);
				assertEquals(
					theirs.getOffset(reading),
					ours.getOffset(reading),
					zoneId + " offset at " + reading
				);
				assertEquals(
					String.valueOf(theirs.getTransition(reading)),
					String.valueOf(ours.getTransition(reading)),
					zoneId + " transition at " + reading
				);
			}
		}
	}

	@Test
	@DisplayName("agrees that a reading in the gap belongs to no offset")
	void agreesAboutAGap() {
		IntlZoneRules ours = derived("America/New_York");
		LocalDateTime missing = LocalDateTime.of(2026, 3, 8, 2, 30);

		assertTrue(ours.getValidOffsets(missing).isEmpty());
		ZoneOffsetTransition gap = ours.getTransition(missing);
		assertNotNull(gap);
		assertTrue(gap.isGap());
		assertEquals(LocalDateTime.of(2026, 3, 8, 2, 0), gap.getDateTimeBefore());
		assertEquals(ZoneOffset.ofHours(-5), gap.getOffsetBefore());
		assertEquals(ZoneOffset.ofHours(-4), gap.getOffsetAfter());
		// the runtime answers a reading that does not exist with the offset before the change
		assertEquals(ZoneOffset.ofHours(-5), ours.getOffset(missing));
	}

	@Test
	@DisplayName("agrees that a reading in the overlap belongs to two offsets, earlier first")
	void agreesAboutAnOverlap() {
		IntlZoneRules ours = derived("America/New_York");
		LocalDateTime twice = LocalDateTime.of(2026, 11, 1, 1, 30);

		assertEquals(
			List.of(ZoneOffset.ofHours(-4), ZoneOffset.ofHours(-5)),
			ours.getValidOffsets(twice)
		);
		ZoneOffsetTransition overlap = ours.getTransition(twice);
		assertNotNull(overlap);
		assertTrue(overlap.isOverlap());
		assertEquals(LocalDateTime.of(2026, 11, 1, 2, 0), overlap.getDateTimeBefore());
		assertEquals(ZoneOffset.ofHours(-4), ours.getOffset(twice));
	}

	@Test
	@DisplayName(
		"agrees on a reading one second either side of a change, and at the edges of a year"
	)
	void agreesAtTheEdges() {
		IntlZoneRules ours = derived("America/New_York");
		java.time.zone.ZoneRules theirs = real("America/New_York");

		for (LocalDateTime reading : List.of(
			LocalDateTime.of(2026, 3, 8, 1, 59, 59),
			LocalDateTime.of(2026, 3, 8, 2, 0, 0),
			LocalDateTime.of(2026, 3, 8, 3, 0, 0),
			LocalDateTime.of(2026, 11, 1, 0, 59, 59),
			LocalDateTime.of(2026, 11, 1, 2, 0, 0),
			LocalDateTime.of(2026, 12, 31, 23, 59, 59),
			LocalDateTime.of(2026, 1, 1, 0, 0, 0),
			LocalDateTime.of(1970, 1, 1, 0, 0, 0)
		)) {
			assertEquals(
				theirs.getValidOffsets(reading),
				ours.getValidOffsets(reading),
				"valid offsets at " + reading
			);
			assertEquals(
				theirs.getOffset(reading),
				ours.getOffset(reading),
				"offset at " + reading
			);
		}
	}

	@Test
	@DisplayName("agrees on whether a reading and an offset go together")
	void agreesOnValidity() {
		IntlZoneRules ours = derived("America/New_York");
		LocalDateTime ordinary = LocalDateTime.of(2026, 6, 1, 12, 0);

		assertTrue(ours.isValidOffset(ordinary, ZoneOffset.ofHours(-4)));
		assertFalse(ours.isValidOffset(ordinary, ZoneOffset.ofHours(-5)));
		assertFalse(ours.isValidOffset(ordinary, null));
	}

	// #endregion
}
