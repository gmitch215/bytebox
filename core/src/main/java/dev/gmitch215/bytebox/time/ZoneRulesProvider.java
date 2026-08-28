package dev.gmitch215.bytebox.time;

import java.time.zone.ZoneRulesException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where {@code java.time} gets its timezone rules.
 *
 * <p>This stands in for the provider a {@code java.time} implementation reads a compiled copy of the
 * timezone database through. The isolate already carries that database behind {@code Intl}, so the
 * rules are derived from what it reports; see {@link IntlZoneRules}. What that saves is the 108 KB the
 * compiled copy occupies plus the reader for it, neither of which becomes reachable.
 *
 * <p>An identifier is accepted if the platform accepts it, which includes the aliases a canonical list
 * leaves out: {@code US/Eastern} resolves even though {@link #getAvailableZoneIds()} does not name it.
 *
 * @since 1.0.0
 */
public class ZoneRulesProvider {

	private static final Map<String, ZoneRules> RULES = new HashMap<>();

	/** Only ever reached by the compiler, when a class it rewrote declares this as its supertype. */
	protected ZoneRulesProvider() {}

	/** {@return every identifier the platform names, which excludes aliases it nonetheless accepts} */
	public static Set<String> getAvailableZoneIds() {
		Set<String> ids = new LinkedHashSet<>();
		for (String id : Zones.available().split(",")) {
			if (!id.isEmpty()) ids.add(id);
		}
		return Collections.unmodifiableSet(ids);
	}

	/**
	 * The rules for an identifier.
	 *
	 * @param zoneId an IANA identifier, such as {@code Europe/London}
	 * @param forCaching whether the caller intends to hold on to the result, which makes no difference
	 *     here because the platform's database changes only when the platform does
	 * @return the rules
	 * @throws ZoneRulesException when the platform does not recognise the identifier
	 */
	public static ZoneRules getRules(String zoneId, boolean forCaching) {
		if (zoneId == null) throw new NullPointerException("zoneId");
		ZoneRules known = RULES.get(zoneId);
		if (known != null) return known;
		if (!Zones.recognises(zoneId)) {
			throw new ZoneRulesException("Unknown time-zone ID: " + zoneId);
		}

		ZoneRules rules = new IntlZoneRules(zoneId, at -> Zones.offsetAt(zoneId, (double) at));
		RULES.put(zoneId, rules);
		return rules;
	}

	/**
	 * The one version of the rules there is.
	 *
	 * @param zoneId an IANA identifier
	 * @return a map holding the single version the platform carries
	 * @throws ZoneRulesException when the platform does not recognise the identifier
	 */
	public static NavigableMap<String, ZoneRules> getVersions(String zoneId) {
		NavigableMap<String, ZoneRules> versions = new TreeMap<>();
		versions.put("Intl", getRules(zoneId, false));
		return versions;
	}

	/** {@return false, because the platform's database changes only when the platform does} */
	public static boolean refresh() {
		return false;
	}
}
