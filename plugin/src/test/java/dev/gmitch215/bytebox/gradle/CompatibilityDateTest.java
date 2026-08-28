package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("checking a compatibility date")
class CompatibilityDateTest {

	@Test
	@DisplayName("reads a real date")
	void reads() {
		assertEquals(LocalDate.of(2026, 8, 22), CompatibilityDate.parse("2026-08-22"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-8-22", "22-08-2026", "August 2026", "2026/08/22", "20260822" })
	@DisplayName("refuses anything that is not yyyy-mm-dd")
	void refusesBadFormats(String text) {
		GradleException failure = assertThrows(GradleException.class, () ->
			CompatibilityDate.parse(text)
		);

		assertTrue(failure.getMessage().contains("yyyy-mm-dd"), failure.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-02-30", "2026-13-01", "2026-00-10", "2025-02-29" })
	@DisplayName("refuses a date the calendar does not have")
	void refusesImpossibleDates(String text) {
		GradleException failure = assertThrows(GradleException.class, () ->
			CompatibilityDate.parse(text)
		);

		assertTrue(failure.getMessage().contains("real calendar date"), failure.getMessage());
	}

	@Test
	@DisplayName("accepts a leap day in a leap year")
	void acceptsLeapDays() {
		assertEquals(LocalDate.of(2028, 2, 29), CompatibilityDate.parse("2028-02-29"));
	}

	@Test
	@DisplayName("says what to set when nothing was")
	void refusesNothing() {
		GradleException failure = assertThrows(GradleException.class, () ->
			CompatibilityDate.parse(null)
		);

		assertTrue(failure.getMessage().contains("compatibilityDate"), failure.getMessage());
	}

	@Test
	@DisplayName("reports a flag that is already the default")
	void reportsRedundantFlags() {
		Map<String, LocalDate> redundant = CompatibilityDate.redundant(
			LocalDate.of(2026, 8, 22),
			List.of("nodejs_compat", "no_nodejs_compat", "something_else")
		);

		assertEquals(1, redundant.size());
		assertTrue(redundant.containsKey("nodejs_compat"));
	}

	@Test
	@DisplayName("says nothing about a flag that is not the default yet for that date")
	void reportsNothingBeforeTheDate() {
		assertTrue(
			CompatibilityDate.redundant(
				LocalDate.of(2026, 1, 1),
				List.of("nodejs_compat")
			).isEmpty()
		);
	}

	@Test
	@DisplayName("knows when Node compatibility became default-on")
	void nodeCompatByDefault() {
		assertTrue(CompatibilityDate.nodeCompatByDefault(LocalDate.of(2026, 8, 4)));
		assertTrue(CompatibilityDate.nodeCompatByDefault(LocalDate.of(2026, 8, 22)));
		assertFalse(CompatibilityDate.nodeCompatByDefault(LocalDate.of(2026, 8, 3)));
	}
}
