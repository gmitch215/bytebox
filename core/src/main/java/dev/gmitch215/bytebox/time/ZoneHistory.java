package dev.gmitch215.bytebox.time;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A zone's recorded history, read from the timezone database ThreeTen-Backport carries.
 *
 * <p>Off unless a project asks for it. The platform's own database answers every question about an
 * offset, and {@link IntlZoneRules} derives the rest from that, but it cannot list the changes a zone
 * has had or the rules by which they recur, so those refuse. Calling {@link #register} installs this
 * as their answer, and the generated entry point does that when the build sets
 * {@code classlib { zoneHistory = true }}. A program that never names this class never links it, and
 * the 108 KB database is only copied into the module when it is linked: see {@link ZoneResources}.
 *
 * <p>The file is read here rather than through ThreeTen's own reader, because that reader builds the
 * rules class this package replaces. What is built instead is plain transitions and rules through
 * their public factories, which is all {@code ZoneRules.getTransitions()} and
 * {@code getTransitionRules()} return.
 *
 * <p>Only these two answers come from the bundle. Offsets, daylight saving and the search for the
 * next change stay with the platform, so a platform whose database is newer than the bundle can
 * disagree with the history it is shown.
 *
 * @since 1.0.3
 */
public final class ZoneHistory implements IntlZoneRules.History {

	/** Where the database sits in the ThreeTen artifact, which is also what {@link ZoneResources} copies. */
	static final String DATA = "org/threeten/bp/TZDB.dat";

	private static final int STANDARD_RULES = 1;

	private final Map<String, Zone> zones = new HashMap<>();

	private String version;
	private String[] regions;
	private short[] ruleOfRegion;
	private byte[][] blobs;

	private ZoneHistory() {}

	/** Makes the recorded history of a zone available, which is what the generated entry point calls. */
	public static void register() {
		IntlZoneRules.history = new ZoneHistory();
	}

	@Override
	public List<ZoneOffsetTransition> transitions(String zoneId) {
		return zone(zoneId, true).transitions;
	}

	@Override
	public List<ZoneOffsetTransitionRule> rules(String zoneId) {
		return zone(zoneId, false).rules;
	}

	@Override
	public boolean covers(String zoneId) {
		if (zones.containsKey(zoneId)) return true;
		load();
		return Arrays.binarySearch(regions, zoneId) >= 0;
	}

	@Override
	public ZoneOffsetTransition next(String zoneId, Instant instant) {
		Zone zone = zone(zoneId, true);
		List<ZoneOffsetTransition> history = zone.transitions;
		if (history.isEmpty()) return null;
		long at = instant.getEpochSecond();
		ZoneOffsetTransition last = history.get(history.size() - 1);
		if (at >= last.toEpochSecond()) {
			if (zone.rules.isEmpty()) return null;
			int year = yearOf(at, last.getOffsetAfter());
			for (ZoneOffsetTransition each : recurring(zone, year)) {
				if (at < each.toEpochSecond()) return each;
			}
			// a JVM stops at Year.MAX_VALUE too
			return year < 999_999_999 ? recurring(zone, year + 1).get(0) : null;
		}
		return history.get(firstAtOrAfter(history, at + 1));
	}

	@Override
	public ZoneOffsetTransition previous(String zoneId, Instant instant) {
		Zone zone = zone(zoneId, true);
		List<ZoneOffsetTransition> history = zone.transitions;
		if (history.isEmpty()) return null;
		long at = instant.getEpochSecond();
		// a reading with a fraction counts as the next second, so a change on the second is past
		if (instant.getNano() > 0 && at < Long.MAX_VALUE) at++;
		ZoneOffsetTransition last = history.get(history.size() - 1);
		if (!zone.rules.isEmpty() && at > last.toEpochSecond()) {
			int year = yearOf(at, last.getOffsetAfter());
			List<ZoneOffsetTransition> thisYear = recurring(zone, year);
			for (int i = thisYear.size() - 1; i >= 0; i--) {
				if (at > thisYear.get(i).toEpochSecond()) return thisYear.get(i);
			}
			if (--year > yearOf(last.toEpochSecond(), last.getOffsetAfter())) {
				List<ZoneOffsetTransition> before = recurring(zone, year);
				return before.get(before.size() - 1);
			}
		}
		int index = firstAtOrAfter(history, at);
		return index <= 0 ? null : history.get(index - 1);
	}

	private static List<ZoneOffsetTransition> recurring(Zone zone, int year) {
		List<ZoneOffsetTransition> changes = new ArrayList<>(zone.rules.size());
		for (ZoneOffsetTransitionRule rule : zone.rules) changes.add(rule.createTransition(year));
		return changes;
	}

	/** The first transition whose instant is {@code at} or later, or the size when there is none. */
	private static int firstAtOrAfter(List<ZoneOffsetTransition> history, long at) {
		int low = 0;
		int high = history.size();
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (history.get(middle).toEpochSecond() < at) low = middle + 1;
			else high = middle;
		}
		return low;
	}

	private static int yearOf(long epochSecond, ZoneOffset offset) {
		return LocalDate.ofEpochDay(
			Math.floorDiv(epochSecond + offset.getTotalSeconds(), 86_400L)
		).getYear();
	}

	private Zone zone(String zoneId, boolean transitions) {
		Zone known = zones.get(zoneId);
		if (known != null) return known;
		load();
		int region = Arrays.binarySearch(regions, zoneId);
		if (region < 0) {
			throw new UnsupportedOperationException(
				"the recorded " +
					(transitions ? "history" : "recurring rules") +
					" of " +
					zoneId +
					" is not in the bundled TZDB " +
					version +
					", which names " +
					regions.length +
					" zones"
			);
		}
		try {
			Zone read = read(blobs[ruleOfRegion[region]]);
			zones.put(zoneId, read);
			return read;
		} catch (IOException corrupt) {
			throw new IllegalStateException(
				"the bundled TZDB is unreadable for " + zoneId,
				corrupt
			);
		}
	}

	private void load() {
		if (regions != null) return;
		try (InputStream stream = ZoneHistory.class.getResourceAsStream("/" + DATA)) {
			if (stream == null) {
				throw new IllegalStateException(
					DATA +
						" is not in the program; the build includes it when the project sets" +
						" classlib { zoneHistory = true }"
				);
			}
			DataInputStream in = new DataInputStream(stream);
			if (in.readByte() != 1 || !"TZDB".equals(in.readUTF())) {
				throw new IllegalStateException(DATA + " is not a TZDB file");
			}
			int versionCount = in.readShort();
			String[] versions = new String[versionCount];
			for (int i = 0; i < versionCount; i++) versions[i] = in.readUTF();
			String[] names = new String[in.readShort()];
			for (int i = 0; i < names.length; i++) names[i] = in.readUTF();
			byte[][] rules = new byte[in.readShort()][];
			for (int i = 0; i < rules.length; i++) {
				rules[i] = new byte[in.readShort()];
				in.readFully(rules[i]);
			}

			// the newest version is the one that answers, as it is for ThreeTen's own provider
			String newest = null;
			short[] newestRules = null;
			String[] newestRegions = null;
			for (int i = 0; i < versionCount; i++) {
				int count = in.readShort();
				String[] regionNames = new String[count];
				short[] ruleIndices = new short[count];
				for (int j = 0; j < count; j++) {
					regionNames[j] = names[in.readShort()];
					ruleIndices[j] = in.readShort();
				}
				if (newest == null || versions[i].compareTo(newest) > 0) {
					newest = versions[i];
					newestRules = ruleIndices;
					newestRegions = regionNames;
				}
			}
			version = newest;
			ruleOfRegion = newestRules;
			blobs = rules;
			regions = newestRegions;
		} catch (IOException corrupt) {
			throw new IllegalStateException("the bundled " + DATA + " is unreadable", corrupt);
		}
	}

	/** One zone's rules in ThreeTen's serial form: standard offset changes, then wall offset changes. */
	private static Zone read(byte[] blob) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob));
		if (in.readByte() != STANDARD_RULES) throw new IOException("unknown rules type");

		int standard = in.readInt();
		for (int i = 0; i < standard; i++) epochSecond(in);
		for (int i = 0; i <= standard; i++) offset(in);

		int changes = in.readInt();
		long[] at = new long[changes];
		for (int i = 0; i < changes; i++) at[i] = epochSecond(in);
		ZoneOffset[] wall = new ZoneOffset[changes + 1];
		for (int i = 0; i <= changes; i++) wall[i] = offset(in);

		List<ZoneOffsetTransition> transitions = new ArrayList<>(changes);
		for (int i = 0; i < changes; i++) {
			transitions.add(
				ZoneOffsetTransition.of(
					LocalDateTime.ofEpochSecond(at[i], 0, wall[i]),
					wall[i],
					wall[i + 1]
				)
			);
		}

		int recurring = in.readByte();
		List<ZoneOffsetTransitionRule> rules = new ArrayList<>(recurring);
		for (int i = 0; i < recurring; i++) rules.add(rule(in));
		return new Zone(
			Collections.unmodifiableList(transitions),
			Collections.unmodifiableList(rules)
		);
	}

	private static ZoneOffsetTransitionRule rule(DataInputStream in) throws IOException {
		int packed = in.readInt();
		Month month = Month.of(packed >>> 28);
		int dayOfMonth = ((packed & (63 << 22)) >>> 22) - 32;
		int weekday = (packed & (7 << 19)) >>> 19;
		int hour = (packed & (31 << 14)) >>> 14;
		TimeDefinition definition = TimeDefinition.values()[(packed & (3 << 12)) >>> 12];
		int standardByte = (packed & (255 << 4)) >>> 4;
		int beforeByte = (packed & (3 << 2)) >>> 2;
		int afterByte = packed & 3;

		int seconds = hour == 31 ? in.readInt() : hour * 3600;
		ZoneOffset standard =
			standardByte == 255
				? ZoneOffset.ofTotalSeconds(in.readInt())
				: ZoneOffset.ofTotalSeconds((standardByte - 128) * 900);
		ZoneOffset before =
			beforeByte == 3
				? ZoneOffset.ofTotalSeconds(in.readInt())
				: ZoneOffset.ofTotalSeconds(standard.getTotalSeconds() + beforeByte * 1800);
		ZoneOffset after =
			afterByte == 3
				? ZoneOffset.ofTotalSeconds(in.readInt())
				: ZoneOffset.ofTotalSeconds(standard.getTotalSeconds() + afterByte * 1800);

		// the public factory only knows a rule at 24:00 as the end of day, so any other time has to fit in one
		boolean endOfDay = seconds == 86_400;
		if (!endOfDay && (seconds < 0 || seconds > 86_399)) {
			throw new IOException("a rule at " + seconds + " seconds into its day");
		}
		return ZoneOffsetTransitionRule.of(
			month,
			dayOfMonth,
			weekday == 0 ? null : DayOfWeek.of(weekday),
			endOfDay ? LocalTime.MIDNIGHT : LocalTime.ofSecondOfDay(seconds),
			endOfDay,
			definition,
			standard,
			before,
			after
		);
	}

	private static ZoneOffset offset(DataInputStream in) throws IOException {
		int quarters = in.readByte();
		return quarters == 127
			? ZoneOffset.ofTotalSeconds(in.readInt())
			: ZoneOffset.ofTotalSeconds(quarters * 900);
	}

	/** Three bytes of quarter hours from 1825 to 2300 when the instant fits, else a marker and a long. */
	private static long epochSecond(DataInputStream in) throws IOException {
		int high = in.readByte() & 255;
		if (high == 255) return in.readLong();
		int middle = in.readByte() & 255;
		int low = in.readByte() & 255;
		return ((high << 16) + (middle << 8) + low) * 900L - 4_575_744_000L;
	}

	private static final class Zone {

		private final List<ZoneOffsetTransition> transitions;
		private final List<ZoneOffsetTransitionRule> rules;

		Zone(List<ZoneOffsetTransition> transitions, List<ZoneOffsetTransitionRule> rules) {
			this.transitions = transitions;
			this.rules = rules;
		}
	}
}
