package dev.gmitch215.bytebox.time;

import static dev.gmitch215.bytebox.time.Rules.derived;
import static dev.gmitch215.bytebox.time.Rules.real;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The recorded history a project can ask to keep, read from the bundled timezone database.
 *
 * <p>Two oracles. A JVM's own rules answer for the zones the spec names, and ThreeTen's own reader
 * answers for every zone in the bundle, which is the same file read by the code this class replaces
 * the use of, so the two cannot differ because of data.
 */
@DisplayName("a zone's recorded history")
class ZoneHistoryTest {

	@AfterEach
	void unregister() {
		IntlZoneRules.history = null;
	}

	// #region against a JVM

	@ParameterizedTest
	@ValueSource(
		strings = {
			"Europe/London",
			"America/New_York",
			"Asia/Kolkata",
			"Australia/Lord_Howe",
			"Africa/Casablanca",
			"Asia/Tokyo",
			"Europe/Moscow"
		}
	)
	@DisplayName("lists the same transitions and rules a JVM does")
	void agreesWithAJVM(String zoneId) {
		ZoneHistory.register();
		IntlZoneRules ours = derived(zoneId);
		java.time.zone.ZoneRules theirs = real(zoneId);

		assertEquals(theirs.getTransitions(), ours.getTransitions(), zoneId + " transitions");
		assertEquals(theirs.getTransitionRules(), ours.getTransitionRules(), zoneId + " rules");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"Europe/London",
			"America/New_York",
			"Asia/Kolkata",
			"Australia/Lord_Howe",
			"Africa/Casablanca",
			"Asia/Tokyo",
			"Europe/Moscow",
			"UTC"
		}
	)
	@DisplayName("finds the next and the previous change wherever a JVM does")
	void changesAgreeWithAJVM(String zoneId) {
		ZoneHistory.register();
		IntlZoneRules ours = derived(zoneId);
		java.time.zone.ZoneRules theirs = real(zoneId);

		List<Instant> instants = new ArrayList<>();
		for (ZoneOffsetTransition each : theirs.getTransitions()) {
			long at = each.toEpochSecond();
			for (long near = at - 1; near <= at + 1; near++) instants.add(
				Instant.ofEpochSecond(near)
			);
			instants.add(Instant.ofEpochSecond(at - 1, 500_000_000));
			instants.add(Instant.ofEpochSecond(at, 1));
		}
		for (long at = -8_000_000_000L; at < 12_000_000_000L; at += 3_199_999L) {
			instants.add(Instant.ofEpochSecond(at));
			instants.add(Instant.ofEpochSecond(at, 999_999_999));
		}
		for (Instant instant : instants) {
			assertEquals(
				theirs.nextTransition(instant),
				ours.nextTransition(instant),
				"next " + instant
			);
			assertEquals(
				theirs.previousTransition(instant),
				ours.previousTransition(instant),
				"previous " + instant
			);
		}
	}

	@Test
	@DisplayName("keeps searching a window when the bundle does not name the zone")
	void searchesWhereTheBundleIsSilent() {
		ZoneHistory.register();
		IntlZoneRules unknown = new IntlZoneRules("Mars/Olympus_Mons", at -> at < 0 ? 0 : 3600);
		Instant before = Instant.parse("1969-12-31T00:00:00Z");

		assertEquals(Instant.EPOCH, unknown.nextTransition(before).getInstant());
		assertEquals(
			Instant.EPOCH,
			unknown.previousTransition(Instant.ofEpochSecond(86_400)).getInstant()
		);
		assertNull(unknown.nextTransition(Instant.ofEpochSecond(86_400)));
	}

	@Test
	@DisplayName("is what ZoneRules answers once it is registered")
	void reachesZoneRules() {
		ZoneHistory.register();
		ZoneRules rules = ZoneRules.forZone(derived("Europe/London"));

		assertEquals(real("Europe/London").getTransitions(), rules.getTransitions());
		assertEquals(real("Europe/London").getTransitionRules(), rules.getTransitionRules());
	}

	@Test
	@DisplayName("is empty for a zone that never changed, and for rules that never recur")
	void isEmptyWhereThereIsNothing() {
		ZoneHistory.register();
		IntlZoneRules kolkata = derived("Asia/Kolkata");

		assertTrue(kolkata.getTransitionRules().isEmpty());
		assertFalse(kolkata.getTransitions().isEmpty());
		assertTrue(ZoneRules.of(ZoneOffset.UTC).getTransitions().isEmpty());
	}

	@Test
	@DisplayName("keeps the last transition and the first rule in step with a JVM")
	void endsWhereAJVMEnds() {
		ZoneHistory.register();
		List<ZoneOffsetTransition> ours = derived("America/New_York").getTransitions();
		List<ZoneOffsetTransition> theirs = real("America/New_York").getTransitions();

		assertEquals(theirs.get(0), ours.get(0));
		assertEquals(theirs.get(theirs.size() - 1), ours.get(ours.size() - 1));
		assertEquals(
			real("America/New_York").getTransitionRules().get(0),
			derived("America/New_York").getTransitionRules().get(0)
		);
	}

	// #endregion

	// #region against the bundle

	@Test
	@DisplayName("reads every zone in the bundle exactly as ThreeTen's own reader does")
	void agreesWithTheBundleForEveryZone() {
		ZoneHistory.register();
		List<String> mismatches = new ArrayList<>();
		int zones = 0;
		for (String id : org.threeten.bp.zone.ZoneRulesProvider.getAvailableZoneIds()) {
			org.threeten.bp.zone.ZoneRules bundled =
				org.threeten.bp.zone.ZoneRulesProvider.getRules(id, false);
			IntlZoneRules ours = new IntlZoneRules(id, at -> 0);
			if (!describe(bundled.getTransitions()).equals(describeOurs(ours.getTransitions()))) {
				mismatches.add(id + " transitions");
			}
			if (
				!describeRules(bundled.getTransitionRules()).equals(
					describeOurRules(ours.getTransitionRules())
				)
			) {
				mismatches.add(id + " rules");
			}
			zones++;
		}
		assertTrue(zones > 500, "the bundle names " + zones + " zones");
		assertEquals(List.of(), mismatches);
	}

	@Test
	@DisplayName("answers the same list twice, and does not let a caller change it")
	void isStable() {
		ZoneHistory.register();
		IntlZoneRules ours = derived("Europe/London");

		List<ZoneOffsetTransition> first = ours.getTransitions();
		assertEquals(first, ours.getTransitions());
		assertThrows(UnsupportedOperationException.class, () -> first.remove(0));
		assertThrows(UnsupportedOperationException.class, () ->
			ours.getTransitionRules().remove(0)
		);
	}

	// #endregion

	// #region when the database is copied in

	@Test
	@DisplayName("is copied into the module only when the program links the history")
	void copiesTheDatabaseOnlyWhenLinked() {
		ZoneResources policy = new ZoneResources();

		assertEquals(0, policy.supplyResources(List.of("com.example.Worker")).length);
		assertEquals(
			List.of("org/threeten/bp/TZDB.dat"),
			List.of(
				policy.supplyResources(List.of("com.example.Worker", ZoneHistory.class.getName()))
			)
		);
		assertNotNull(ZoneHistory.class.getResource("/org/threeten/bp/TZDB.dat"));
	}

	@Test
	@DisplayName("is found by the compiler through its services file")
	void isDiscovered() {
		List<Class<?>> found = new ArrayList<>();
		for (org.teavm.extension.spi.resources.ResourcesPolicy each : java.util.ServiceLoader.load(
			org.teavm.extension.spi.resources.ResourcesPolicy.class
		)) {
			found.add(each.getClass());
		}
		assertTrue(found.contains(ZoneResources.class), found.toString());
	}

	// #endregion

	// #region what it refuses

	@Test
	@DisplayName("names the zone and the bundle when the bundle has no record of it")
	void refusesAZoneTheBundleLacks() {
		ZoneHistory.register();
		IntlZoneRules unknown = new IntlZoneRules("Mars/Olympus_Mons", at -> 0);

		for (Runnable ask : List.<Runnable>of(
			unknown::getTransitions,
			unknown::getTransitionRules
		)) {
			String message = assertThrows(
				UnsupportedOperationException.class,
				ask::run
			).getMessage();
			assertTrue(message.contains("Mars/Olympus_Mons"), message);
			assertTrue(message.contains("TZDB"), message);
		}
	}

	@Test
	@DisplayName("still refuses until something registers it")
	void refusesByDefault() {
		assertNull(IntlZoneRules.history);
		assertThrows(UnsupportedOperationException.class, derived("Europe/London")::getTransitions);
		assertThrows(
			UnsupportedOperationException.class,
			derived("Europe/London")::getTransitionRules
		);

		ZoneHistory.register();
		assertNotNull(IntlZoneRules.history);
	}

	// #endregion

	private static String describe(List<org.threeten.bp.zone.ZoneOffsetTransition> list) {
		StringBuilder out = new StringBuilder();
		for (org.threeten.bp.zone.ZoneOffsetTransition each : list) {
			out.append(each.toEpochSecond())
				.append(' ')
				.append(each.getOffsetBefore().getTotalSeconds())
				.append(' ')
				.append(each.getOffsetAfter().getTotalSeconds())
				.append('\n');
		}
		return out.toString();
	}

	private static String describeOurs(List<ZoneOffsetTransition> list) {
		StringBuilder out = new StringBuilder();
		for (ZoneOffsetTransition each : list) {
			out.append(each.toEpochSecond())
				.append(' ')
				.append(each.getOffsetBefore().getTotalSeconds())
				.append(' ')
				.append(each.getOffsetAfter().getTotalSeconds())
				.append('\n');
		}
		return out.toString();
	}

	private static String describeRules(List<org.threeten.bp.zone.ZoneOffsetTransitionRule> list) {
		StringBuilder out = new StringBuilder();
		for (org.threeten.bp.zone.ZoneOffsetTransitionRule each : list) {
			out.append(each.getMonth())
				.append(' ')
				.append(each.getDayOfMonthIndicator())
				.append(' ')
				.append(each.getDayOfWeek())
				.append(' ')
				.append(each.getLocalTime())
				.append(' ')
				.append(each.isMidnightEndOfDay())
				.append(' ')
				.append(each.getTimeDefinition())
				.append(' ')
				.append(each.getStandardOffset().getTotalSeconds())
				.append(' ')
				.append(each.getOffsetBefore().getTotalSeconds())
				.append(' ')
				.append(each.getOffsetAfter().getTotalSeconds())
				.append('\n');
		}
		return out.toString();
	}

	private static String describeOurRules(List<ZoneOffsetTransitionRule> list) {
		StringBuilder out = new StringBuilder();
		for (ZoneOffsetTransitionRule each : list) {
			out.append(each.getMonth())
				.append(' ')
				.append(each.getDayOfMonthIndicator())
				.append(' ')
				.append(each.getDayOfWeek())
				.append(' ')
				.append(each.getLocalTime())
				.append(' ')
				.append(each.isMidnightEndOfDay())
				.append(' ')
				.append(each.getTimeDefinition())
				.append(' ')
				.append(each.getStandardOffset().getTotalSeconds())
				.append(' ')
				.append(each.getOffsetBefore().getTotalSeconds())
				.append(' ')
				.append(each.getOffsetAfter().getTotalSeconds())
				.append('\n');
		}
		return out.toString();
	}
}
