package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code %t} against a JVM's own {@code String.format}.
 *
 * <p>A JVM reads an epoch value in its default zone, so the default is pinned to UTC for the run, which
 * is the zone a Worker is in. Each row is one format over one argument: it passes when both sides give
 * the same text, or both fail with the same exception class and message.
 */
@DisplayName("the date conversions")
class DatesTest {

	private static final String SUPPORTED = "HIklMSLNpzsQBbhAaCYyjmdeRTrDF";

	private static TimeZone saved;

	@BeforeAll
	static void pinTheZone() {
		saved = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@AfterAll
	static void restoreTheZone() {
		TimeZone.setDefault(saved);
	}

	@BeforeEach
	void install() {
		Dates.register();
	}

	@AfterEach
	void uninstall() {
		DateConversion.installed = null;
	}

	private static String ours(Locale locale, String format, Object... args) {
		return new Formatter(new StringBuilder(), locale, languageTag -> ".,")
			.format(format, args)
			.toString();
	}

	private static void agree(Locale locale, String format, Object... args) {
		String label =
			"format " + format + " locale " + locale + " args " + java.util.Arrays.toString(args);
		String expected = null;
		String expectedFailure = null;
		try {
			expected = String.format(locale, format, args);
		} catch (RuntimeException failure) {
			expectedFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
		}
		String actual = null;
		String actualFailure = null;
		try {
			actual = ours(locale, format, args);
		} catch (RuntimeException failure) {
			actualFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
		}
		assertEquals(expectedFailure, actualFailure, label);
		assertEquals(expected, actual, label);
	}

	private static List<Long> instants() {
		return List.of(
			0L,
			1L,
			999L,
			-1L,
			-1500L,
			43_200_000L,
			46_800_001L,
			951_782_400_999L,
			1_700_000_000_123L,
			1_757_000_000_000L,
			4_102_444_800_000L,
			253_402_300_799_999L,
			-12_219_292_800_000L,
			-12_219_292_799_999L,
			-2_208_988_800_000L,
			-62_135_596_800_000L + 1_000_000_000_000_000L
		);
	}

	private static List<Object> arguments() {
		List<Object> all = new ArrayList<>();
		for (long at : instants()) {
			if (at < -12_219_292_800_000L) continue;
			all.add(at);
			all.add(new Date(at));
			Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
			utc.setTimeInMillis(at);
			all.add(utc);
			for (String zone : List.of("America/New_York", "Asia/Kolkata", "Australia/Lord_Howe")) {
				Calendar other = Calendar.getInstance(TimeZone.getTimeZone(zone), Locale.US);
				other.setTimeInMillis(at);
				all.add(other);
			}
		}
		Instant instant = Instant.ofEpochMilli(1_757_000_000_123L);
		all.add(LocalDate.of(2026, 10, 9));
		all.add(LocalDate.of(2024, 2, 29));
		all.add(LocalTime.of(13, 5, 9, 123_456_789));
		all.add(LocalTime.MIDNIGHT);
		all.add(LocalDateTime.of(2026, 10, 9, 0, 0, 0));
		all.add(LocalDateTime.of(1999, 12, 31, 12, 30, 45, 5_000_000));
		all.add(ZonedDateTime.ofInstant(instant, ZoneId.of("America/New_York")));
		all.add(ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Kolkata")));
		all.add(OffsetDateTime.ofInstant(instant, ZoneOffset.ofHoursMinutes(-3, -30)));
		all.add(instant);
		all.add(LocalDate.of(-5, 1, 1));
		all.add(LocalDate.of(12345, 1, 1));
		all.add(java.time.Year.of(2026));
		return all;
	}

	@ParameterizedTest
	@ValueSource(strings = { "en_US", "en", "none" })
	@DisplayName("writes every supported conversion as a JVM does, for every kind of argument")
	void matchesAJVM(String tag) {
		Locale locale = tag.equals("none") ? null : tag.equals("en") ? Locale.ENGLISH : Locale.US;
		List<Object> arguments = arguments();
		int rows = 0;
		for (char suffix : SUPPORTED.toCharArray()) {
			for (Object argument : arguments) {
				// JDK 21 names Y in the failure and 25 names F, so only the exception class is compared
				if (
					suffix == 'F' &&
					argument instanceof java.time.temporal.TemporalAccessor temporal &&
					!temporal.isSupported(java.time.temporal.ChronoField.EPOCH_DAY)
				) continue;
				// JDK 21 writes the year of era here, and 22 and later write the signed ISO year
				if (
					suffix == 'F' &&
					argument instanceof LocalDate date &&
					(date.getYear() < 1 || date.getYear() > 9999)
				) continue;
				agree(locale, "%t" + suffix, argument);
				agree(locale, "%T" + suffix, argument);
				rows += 2;
			}
		}
		assertTrue(rows > 3000, "only " + rows + " rows");
	}

	@Test
	@DisplayName("pads, justifies and reuses an argument as a JVM does")
	void matchesOnWidthAndIndex() {
		for (String format : List.of(
			"%20tF|",
			"%-20tF|",
			"%10tY|",
			"%-10tY|",
			"%TB",
			"%tY %<tm %<td",
			"%1$tY-%1$tm-%1$td",
			"%tY%%",
			"%tH:%tM",
			"%10tp|",
			"%-8tB|"
		)) {
			agree(Locale.US, format, 1_757_000_000_123L, 1_757_000_000_123L);
		}
	}

	@Test
	@DisplayName("writes null, and fails on a wrong type or a bad flag, as a JVM does")
	void matchesOnFailures() {
		for (String format : List.of(
			"%tY",
			"%10tY|",
			"%TY",
			"%+tY",
			"%#tY",
			"% tY",
			"%0tY",
			"%,tY",
			"%(tY",
			"%-tY",
			"%.2tY",
			"%tq",
			"%t",
			"%<tY"
		)) {
			agree(Locale.US, format, (Object) null);
			agree(Locale.US, format, 5L);
		}
		for (Object wrong : new Object[] { "x", 1, 1.5, true, new Object() }) {
			agree(Locale.US, "%tY", wrong);
			agree(Locale.US, "%TH", wrong);
		}
		agree(Locale.US, "%tH", LocalDate.of(2026, 1, 1));
		agree(Locale.US, "%tY", LocalTime.of(1, 2));
		agree(Locale.US, "%tz", LocalDateTime.of(2026, 1, 1, 0, 0));
		assertEquals("-0005-01-01", ours(Locale.US, "%tF", LocalDate.of(-5, 1, 1)));
		assertEquals("+12345-01-01", ours(Locale.US, "%tF", LocalDate.of(12345, 1, 1)));
		for (Object undated : new Object[] {
			LocalTime.of(1, 2),
			Instant.EPOCH,
			java.time.Year.of(2026)
		}) {
			assertThrows(java.util.IllegalFormatConversionException.class, () ->
				ours(Locale.US, "%tF", undated)
			);
		}
	}

	@Test
	@DisplayName("refuses the zone name, and a locale whose names it does not have, saying why")
	void refusesWhatItCannotWrite() {
		for (String format : List.of("%tZ", "%tc", "%TZ")) {
			String message = assertThrows(UnsupportedOperationException.class, () ->
				ours(Locale.US, format, 0L)
			).getMessage();
			assertTrue(message.contains("short name"), message);
		}
		for (Locale locale : List.of(Locale.UK, Locale.CANADA, Locale.FRANCE, Locale.ROOT)) {
			String message = assertThrows(UnsupportedOperationException.class, () ->
				ours(locale, "%tB", 0L)
			).getMessage();
			assertTrue(message.contains("Locale.US"), message);
		}
		String message = assertThrows(UnsupportedOperationException.class, () ->
			ours(Locale.US, "%tY", -12_219_292_800_001L)
		).getMessage();
		assertTrue(message.contains("Julian"), message);
	}

	@Test
	@DisplayName("refuses as before until something installs it")
	void refusesByDefault() {
		DateConversion.installed = null;

		assertNull(DateConversion.installed);
		assertTrue(
			assertThrows(UnsupportedOperationException.class, () -> ours(Locale.US, "%tY", 0L))
				.getMessage()
				.contains("DateTimeFormatter")
		);
	}
}
