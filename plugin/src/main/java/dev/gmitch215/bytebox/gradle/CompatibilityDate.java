package dev.gmitch215.bytebox.gradle;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;

/**
 * Checks a compatibility date before Wrangler has to.
 *
 * <p>Three checks, in increasing authority. Format and calendar are decided here, because they are
 * decidable here. Redundant flags are a warning, matching Cloudflare's own position that a flag
 * already default-on is ignored. Whether the installed runtime knows a date is not decidable here at
 * all — the set of valid dates is a property of the installed Wrangler and moves — so that one is
 * left to {@code wrangler deploy --dry-run}.
 *
 * @since 1.0.0
 */
public final class CompatibilityDate {

	/**
	 * Flags that became the default on a given date, so declaring them again is redundant.
	 *
	 * <p>Hand-maintained is how this goes stale, which is why {@code refreshCompatFlags} regenerates
	 * it from Cloudflare's own page.
	 */
	private static final Map<String, LocalDate> DEFAULT_ON = defaults();

	private CompatibilityDate() {}

	private static Map<String, LocalDate> defaults() {
		Map<String, LocalDate> flags = new LinkedHashMap<>();
		flags.put("nodejs_compat", LocalDate.of(2026, 8, 4));
		flags.put("nodejs_compat_v2", LocalDate.of(2026, 8, 4));
		flags.put("global_fetch_strictly_public", LocalDate.of(2024, 3, 1));
		flags.put("streams_enable_constructors", LocalDate.of(2022, 11, 30));
		flags.put("transformstream_enable_standard_constructor", LocalDate.of(2022, 11, 30));
		flags.put("no_cots_on_external_fetch", LocalDate.of(2024, 3, 4));
		return Map.copyOf(flags);
	}

	/**
	 * Reads a date, refusing anything Wrangler would refuse for a reason decidable here.
	 *
	 * @param text the date, as {@code yyyy-mm-dd}
	 * @return the parsed date
	 */
	public static LocalDate parse(String text) {
		if (text == null || text.isBlank()) {
			throw new GradleException(
				"a compatibility date is required; set bytebox.wrangler.compatibilityDate"
			);
		}
		if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) {
			throw new GradleException(
				"the compatibility date " + text + " is not in yyyy-mm-dd form"
			);
		}
		try {
			return LocalDate.parse(text);
		} catch (DateTimeParseException notADate) {
			throw new GradleException(
				"the compatibility date " + text + " is not a real calendar date",
				notADate
			);
		}
	}

	/**
	 * Flags that are already the default for a date, and so do nothing.
	 *
	 * <p>Reported rather than refused, because Cloudflare ignores a redundant flag and refusing one
	 * would break a configuration that works.
	 *
	 * @param date the compatibility date
	 * @param flags the declared flags
	 * @return the redundant ones, with the date each became the default
	 */
	public static Map<String, LocalDate> redundant(LocalDate date, List<String> flags) {
		Map<String, LocalDate> redundant = new LinkedHashMap<>();
		for (String flag : flags) {
			LocalDate since = DEFAULT_ON.get(flag);
			if (since != null && !date.isBefore(since)) redundant.put(flag, since);
		}
		return redundant;
	}

	/**
	 * Whether a date has Node compatibility on by default.
	 *
	 * <p>Which decides whether the generated configuration has to turn it off. Java needs no Node
	 * polyfills, the v2 flag is documented as increasing bundle size, and the compiler's runtime
	 * treats a {@code process} global as proof it is running under Node.
	 *
	 * @param date the compatibility date
	 * @return whether the flags have to be disabled explicitly
	 */
	public static boolean nodeCompatByDefault(LocalDate date) {
		return !date.isBefore(DEFAULT_ON.get("nodejs_compat"));
	}
}
