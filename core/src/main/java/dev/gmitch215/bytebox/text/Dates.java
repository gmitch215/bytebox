package dev.gmitch215.bytebox.text;

import java.time.DateTimeException;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Calendar;
import java.util.Date;
import java.util.FormatFlagsConversionMismatchException;
import java.util.IllegalFormatConversionException;
import java.util.IllegalFormatPrecisionException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;

/**
 * {@code %t} and {@code %T} for {@code String.format}, which a project switches on with
 * {@code classlib { dateFormat = true }}.
 *
 * <p>The arguments are a {@code long}, a {@code Long}, a {@link Date}, a {@link Calendar} or a
 * {@link TemporalAccessor}, which are the ones a JVM takes. An epoch value, and a {@code Date}, are
 * read in UTC, which is the zone a Worker runs in; a {@code Calendar} and a temporal carry their own.
 * Every conversion a JVM has is here except two:
 *
 * <ul>
 *   <li>{@code %tZ}, and {@code %tc} which contains it, are the zone's short name, which is data the
 *       platform does not hand over in the form a JVM prints.
 *   <li>An epoch value before 1582-10-15 is read by a JVM in the Julian calendar, which is not
 *       implemented here.
 * </ul>
 *
 * <p>Names and the am/pm marker are written in English, so {@code Locale.US}, {@code Locale.ENGLISH}
 * and no locale at all are accepted and any other locale is refused: a JVM's {@code en_GB} writes
 * {@code Sept} and its {@code en_CA} writes {@code p.m.}, which is data this class does not have.
 *
 * @since 1.0.3
 */
public final class Dates extends DateConversion {

	private static final String[] MONTHS = {
		"January",
		"February",
		"March",
		"April",
		"May",
		"June",
		"July",
		"August",
		"September",
		"October",
		"November",
		"December"
	};

	private static final String[] DAYS = {
		"Monday",
		"Tuesday",
		"Wednesday",
		"Thursday",
		"Friday",
		"Saturday",
		"Sunday"
	};

	private static final String SUFFIXES = "HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc";

	/** 1582-10-15T00:00Z, where a JVM's calendar changes from the Julian to the Gregorian one. */
	private static final long CUTOVER = -12_219_292_800_000L;

	private Dates() {}

	/** Makes {@code %t} work, which is what the generated entry point calls. */
	public static void register() {
		installed = new Dates();
	}

	@Override
	char suffix(
		String format,
		int at,
		char conversion,
		int precision,
		boolean alternate,
		boolean signed,
		boolean space,
		boolean zero,
		boolean grouped,
		boolean parenthesised
	) {
		if (at >= format.length()) throw new UnknownFormatConversionException(
			String.valueOf(conversion)
		);
		char suffix = format.charAt(at);
		if (precision >= 0) throw new IllegalFormatPrecisionException(precision);
		if (SUFFIXES.indexOf(suffix) < 0) throw new UnknownFormatConversionException("t" + suffix);
		String bad = alternate
			? "#"
			: signed
				? "+"
				: space
					? " "
					: zero
						? "0"
						: grouped
							? ","
							: parenthesised
								? "("
								: null;
		if (bad != null) throw new FormatFlagsConversionMismatchException(bad, suffix);
		return suffix;
	}

	@Override
	String format(char suffix, Object argument, Locale locale) {
		Moment moment;
		if (argument instanceof Long) moment = epoch((Long) argument);
		else if (argument instanceof Date) moment = epoch(((Date) argument).getTime());
		else if (argument instanceof Calendar) moment = new CalendarMoment((Calendar) argument);
		else if (argument instanceof TemporalAccessor) {
			moment = new TemporalMoment((TemporalAccessor) argument);
		} else throw new IllegalFormatConversionException(suffix, argument.getClass());

		if (locale != null && !locale.equals(Locale.US) && !locale.equals(Locale.ENGLISH)) {
			throw new UnsupportedOperationException(
				"%t is only written for Locale.US and Locale.ENGLISH here, because the month, day and" +
					" am/pm names of " +
					locale +
					" are not available; DateTimeFormatter formats a date in a locale"
			);
		}
		if (suffix == 'Z' || suffix == 'c') {
			throw new UnsupportedOperationException(
				"%t" +
					suffix +
					" is not supported here, because it writes the zone's short name, which the platform" +
					" does not give in the form a JVM prints; %tz writes the offset"
			);
		}
		moment.type = argument.getClass();
		StringBuilder out = new StringBuilder();
		append(out, suffix, moment);
		return out.toString();
	}

	/** A conversion over a temporal that lacks a field names itself, so a composite names the one it needed. */
	private static void append(StringBuilder out, char suffix, Moment m) {
		try {
			write(out, suffix, m);
		} catch (DateTimeException unsupported) {
			throw new IllegalFormatConversionException(suffix, m.type);
		}
	}

	private static void write(StringBuilder out, char suffix, Moment m) {
		switch (suffix) {
			case 'H':
				out.append(pad(m.hourOfDay(), 2));
				break;
			case 'k':
				out.append(m.hourOfDay());
				break;
			case 'I':
				out.append(pad(m.clockHour(), 2));
				break;
			case 'l':
				out.append(m.clockHour());
				break;
			case 'M':
				out.append(pad(m.minute(), 2));
				break;
			case 'S':
				out.append(pad(m.second(), 2));
				break;
			case 'L':
				out.append(pad(m.milli(), 3));
				break;
			case 'N':
				out.append(pad(m.nano(), 9));
				break;
			case 'p':
				out.append(m.amPm() == 0 ? "am" : "pm");
				break;
			case 's':
				out.append(m.epochSecond());
				break;
			case 'Q':
				out.append(m.epochMilli());
				break;
			case 'z':
				offset(out, m.offsetSeconds());
				break;
			case 'A':
				out.append(DAYS[m.dayOfWeek() - 1]);
				break;
			case 'a':
				out.append(DAYS[m.dayOfWeek() - 1], 0, 3);
				break;
			case 'B':
				out.append(MONTHS[m.month() - 1]);
				break;
			case 'b':
			case 'h':
				out.append(MONTHS[m.month() - 1], 0, 3);
				break;
			case 'C':
				out.append(pad(m.year() / 100, 2));
				break;
			case 'y':
				out.append(pad(m.year() % 100, 2));
				break;
			case 'Y':
				out.append(pad(m.year(), 4));
				break;
			case 'j':
				out.append(pad(m.dayOfYear(), 3));
				break;
			case 'm':
				out.append(pad(m.month(), 2));
				break;
			case 'd':
				out.append(pad(m.dayOfMonth(), 2));
				break;
			case 'e':
				out.append(m.dayOfMonth());
				break;
			case 'T':
				append(out, 'H', m);
				out.append(':');
				append(out, 'M', m);
				out.append(':');
				append(out, 'S', m);
				break;
			case 'R':
				append(out, 'H', m);
				out.append(':');
				append(out, 'M', m);
				break;
			case 'r':
				append(out, 'I', m);
				out.append(':');
				append(out, 'M', m);
				out.append(':');
				append(out, 'S', m);
				out.append(m.amPm() == 0 ? " AM" : " PM");
				break;
			case 'D':
				append(out, 'm', m);
				out.append('/');
				append(out, 'd', m);
				out.append('/');
				append(out, 'y', m);
				break;
			case 'F':
				m.isoDate(out);
				break;
			default:
				throw new IllegalStateException("no conversion " + suffix);
		}
	}

	private static void offset(StringBuilder out, int seconds) {
		boolean negative = seconds < 0;
		int minutes = Math.abs(seconds) / 60;
		out.append(negative ? '-' : '+').append(pad((minutes / 60) * 100 + (minutes % 60), 4));
	}

	private static String pad(long value, int width) {
		String digits = Long.toString(value);
		StringBuilder out = new StringBuilder();
		for (int i = digits.length(); i < width; i++) out.append('0');
		return out.append(digits).toString();
	}

	/** Reads an epoch value in UTC, with the arithmetic a calendar does. */
	private static Moment epoch(long millis) {
		if (millis < CUTOVER) {
			throw new UnsupportedOperationException(
				"%t of an epoch value before 1582-10-15 is not supported here, because a JVM reads it in" +
					" the Julian calendar; a LocalDate holds the proleptic Gregorian date"
			);
		}
		long seconds = Math.floorDiv(millis, 1000L);
		long days = Math.floorDiv(seconds, 86_400L);
		int secondOfDay = (int) Math.floorMod(seconds, 86_400L);

		long shifted = days + 719_468;
		long era = Math.floorDiv(shifted, 146_097L);
		long dayOfEra = shifted - era * 146_097;
		long yearOfEra =
			(dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
		long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
		long shiftedMonth = (5 * dayOfYear + 2) / 153;
		int day = (int) (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1);
		int month = (int) (shiftedMonth < 10 ? shiftedMonth + 3 : shiftedMonth - 9);
		int year = (int) (yearOfEra + era * 400 + (month <= 2 ? 1 : 0));

		boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
		int[] before = { 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334 };
		// the ten days a JVM drops in October 1582 are missing from that year
		int ordinal =
			before[month - 1] + day + (leap && month > 2 ? 1 : 0) - (year == 1582 ? 10 : 0);

		return new Fixed(
			year,
			month,
			day,
			ordinal,
			(int) Math.floorMod(days + 3, 7L) + 1,
			secondOfDay / 3600,
			(secondOfDay / 60) % 60,
			secondOfDay % 60,
			(int) Math.floorMod(millis, 1000L),
			0,
			millis
		);
	}

	// #region what a conversion reads

	private abstract static class Moment {

		Class<?> type;

		abstract int year();

		abstract int month();

		abstract int dayOfMonth();

		abstract int dayOfYear();

		/** One for Monday to seven for Sunday. */
		abstract int dayOfWeek();

		abstract int hourOfDay();

		abstract int clockHour();

		abstract int minute();

		abstract int second();

		abstract int milli();

		abstract int nano();

		abstract int amPm();

		abstract int offsetSeconds();

		abstract long epochSecond();

		abstract long epochMilli();

		abstract void isoDate(StringBuilder out);

		final void plainDate(StringBuilder out) {
			out.append(pad(year(), 4)).append('-').append(pad(month(), 2)).append('-');
			out.append(pad(dayOfMonth(), 2));
		}
	}

	/** Fields worked out up front, for an epoch value and for a calendar. */
	private static class Fixed extends Moment {

		private final int year;
		private final int month;
		private final int day;
		private final int ordinal;
		private final int weekday;
		private final int hour;
		private final int minute;
		private final int second;
		private final int milli;
		private final int offset;
		private final long epochMilli;

		Fixed(
			int year,
			int month,
			int day,
			int ordinal,
			int weekday,
			int hour,
			int minute,
			int second,
			int milli,
			int offset,
			long epochMilli
		) {
			this.year = year;
			this.month = month;
			this.day = day;
			this.ordinal = ordinal;
			this.weekday = weekday;
			this.hour = hour;
			this.minute = minute;
			this.second = second;
			this.milli = milli;
			this.offset = offset;
			this.epochMilli = epochMilli;
		}

		@Override
		int year() {
			return year;
		}

		@Override
		int month() {
			return month;
		}

		@Override
		int dayOfMonth() {
			return day;
		}

		@Override
		int dayOfYear() {
			return ordinal;
		}

		@Override
		int dayOfWeek() {
			return weekday;
		}

		@Override
		int hourOfDay() {
			return hour;
		}

		@Override
		int clockHour() {
			return hour == 0 || hour == 12 ? 12 : hour % 12;
		}

		@Override
		int minute() {
			return minute;
		}

		@Override
		int second() {
			return second;
		}

		@Override
		int milli() {
			return milli;
		}

		@Override
		int nano() {
			return milli * 1_000_000;
		}

		@Override
		int amPm() {
			return hour < 12 ? 0 : 1;
		}

		@Override
		int offsetSeconds() {
			return offset;
		}

		@Override
		long epochSecond() {
			return epochMilli / 1000;
		}

		@Override
		long epochMilli() {
			return epochMilli;
		}

		@Override
		void isoDate(StringBuilder out) {
			plainDate(out);
		}
	}

	private static final class CalendarMoment extends Fixed {

		CalendarMoment(Calendar calendar) {
			super(
				calendar.get(Calendar.YEAR),
				calendar.get(Calendar.MONTH) + 1,
				calendar.get(Calendar.DATE),
				calendar.get(Calendar.DAY_OF_YEAR),
				((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1,
				calendar.get(Calendar.HOUR_OF_DAY),
				calendar.get(Calendar.MINUTE),
				calendar.get(Calendar.SECOND),
				calendar.get(Calendar.MILLISECOND),
				(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 1000,
				calendar.getTimeInMillis()
			);
		}
	}

	/** Fields read on demand, because a date has no hour and a time has no day. */
	private static final class TemporalMoment extends Moment {

		private final TemporalAccessor time;

		TemporalMoment(TemporalAccessor time) {
			this.time = time;
		}

		@Override
		int year() {
			return time.get(ChronoField.YEAR_OF_ERA);
		}

		@Override
		int month() {
			return time.get(ChronoField.MONTH_OF_YEAR);
		}

		@Override
		int dayOfMonth() {
			return time.get(ChronoField.DAY_OF_MONTH);
		}

		@Override
		int dayOfYear() {
			return time.get(ChronoField.DAY_OF_YEAR);
		}

		@Override
		int dayOfWeek() {
			return time.get(ChronoField.DAY_OF_WEEK);
		}

		@Override
		int hourOfDay() {
			return time.get(ChronoField.HOUR_OF_DAY);
		}

		@Override
		int clockHour() {
			return time.get(ChronoField.CLOCK_HOUR_OF_AMPM);
		}

		@Override
		int minute() {
			return time.get(ChronoField.MINUTE_OF_HOUR);
		}

		@Override
		int second() {
			return time.get(ChronoField.SECOND_OF_MINUTE);
		}

		@Override
		int milli() {
			return time.get(ChronoField.MILLI_OF_SECOND);
		}

		@Override
		int nano() {
			try {
				return time.get(ChronoField.NANO_OF_SECOND);
			} catch (UnsupportedTemporalTypeException noNanos) {
				return time.get(ChronoField.MILLI_OF_SECOND) * 1_000_000;
			}
		}

		@Override
		int amPm() {
			return time.get(ChronoField.AMPM_OF_DAY);
		}

		@Override
		int offsetSeconds() {
			return time.get(ChronoField.OFFSET_SECONDS);
		}

		@Override
		long epochSecond() {
			return time.getLong(ChronoField.INSTANT_SECONDS);
		}

		@Override
		long epochMilli() {
			return (
				time.getLong(ChronoField.INSTANT_SECONDS) * 1000L +
				time.getLong(ChronoField.MILLI_OF_SECOND)
			);
		}

		@Override
		void isoDate(StringBuilder out) {
			ChronoField field =
				time.query(TemporalQueries.chronology()) instanceof IsoChronology
					? ChronoField.YEAR
					: ChronoField.YEAR_OF_ERA;
			int year = time.get(field);
			if (year < 0) {
				out.append('-');
				year = -year;
			} else if (year > 9999) {
				out.append('+');
			}
			out.append(pad(year, 4)).append('-').append(pad(month(), 2)).append('-');
			out.append(pad(dayOfMonth(), 2));
		}
	}

	// #endregion
}
