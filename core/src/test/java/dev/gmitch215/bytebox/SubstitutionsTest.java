package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.extension.spi.substitution.ClassSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionPolicy;

/**
 * Which class names each policy claims, and what it rewrites them to.
 *
 * <p>Worth checking here rather than by compiling, because both ways this goes wrong are silent. A
 * predicate that is too wide sweeps in the class holding the calls into JavaScript, and a substituted
 * class loses those. A predicate that is too narrow leaves the class library's own version in place,
 * which compiles and then fails at the first call.
 */
@DisplayName("the substitution policies")
class SubstitutionsTest {

	@Test
	@DisplayName("replaces the java.net classes the platform cannot back, and no others")
	void net() {
		List<Selection> selections = install(new dev.gmitch215.bytebox.net.Substitutions());

		assertEquals(2, selections.size());
		Selection classes = selections.get(0);
		assertTrue(classes.matches("java.net.Socket"));
		assertTrue(classes.matches("java.net.URL"));
		assertTrue(classes.matches("java.net.URLConnection"));
		assertTrue(classes.matches("java.net.HttpURLConnection"));
		assertTrue(classes.matches("java.net.InetAddress"));
		assertTrue(classes.matches("java.net.UnknownHostException"));
		assertTrue(classes.matches("java.net.SocketException"));
		assertTrue(classes.matches("java.net.ConnectException"));
		assertTrue(classes.matches("java.net.SocketTimeoutException"));
		assertEquals(List.of("java.net", "dev.gmitch215.bytebox.net"), classes.replacement());

		// the platform accepts no inbound connection and speaks no UDP, so a program using either
		// should fail while it is being built rather than once it is running
		assertFalse(classes.matches("java.net.ServerSocket"));
		assertFalse(classes.matches("java.net.DatagramSocket"));
		assertFalse(classes.matches("java.net.NetworkInterface"));
	}

	@Test
	@DisplayName("names the HTTP client by pattern, leaving the class that holds the fetch calls")
	void netHttp() {
		Selection http = install(new dev.gmitch215.bytebox.net.Substitutions()).get(1);

		assertTrue(http.matches("java.net.http.HttpClient"));
		assertTrue(http.matches("java.net.http.HttpRequest"));
		assertTrue(http.matches("java.net.http.HttpResponse"));
		assertTrue(http.matches("java.net.http.HttpHeaders"));
		assertFalse(http.matches("java.net.http.Fetches"));
		assertFalse(http.matches("java.net.http.WebSocket"));
		assertEquals(
			List.of("java.net.http", "dev.gmitch215.bytebox.net.http"),
			http.replacement()
		);
	}

	@Test
	@DisplayName("sends the zone rules here and the rest of java.time to the backport")
	void time() {
		List<Selection> selections = install(new dev.gmitch215.bytebox.time.Substitutions());

		assertEquals(3, selections.size());
		Selection rules = selections.get(0);
		assertTrue(rules.matches("java.time.zone.ZoneRules"));
		assertTrue(rules.matches("java.time.zone.ZoneRulesProvider"));
		assertFalse(rules.matches("java.time.zone.ZoneOffsetTransition"));
		assertEquals(List.of("java.time.zone", "dev.gmitch215.bytebox.time"), rules.replacement());

		Selection backport = selections.get(1);
		assertTrue(backport.matches("org.threeten.bp.zone.ZoneRules"));
		assertTrue(backport.matches("org.threeten.bp.zone.ZoneRulesProvider"));

		Selection rest = selections.get(2);
		assertTrue(rest.matches("java.time.LocalDate"));
		assertTrue(rest.matches("java.time.format.DateTimeFormatter"));
		assertEquals(List.of("java.time", "org.threeten.bp"), rest.replacement());
		assertFalse(rest.matches("dev.gmitch215.bytebox.time.Zones"));
	}

	@Test
	@DisplayName("replaces the formatter without claiming the class that calls into Intl")
	void text() {
		Selection selection = install(new dev.gmitch215.bytebox.text.Substitutions()).get(0);

		assertTrue(selection.matches("java.util.Formatter"));
		assertFalse(selection.matches("dev.gmitch215.bytebox.text.Numbers"));
		assertFalse(selection.matches("dev.gmitch215.bytebox.text.Separators"));
	}

	@Test
	@DisplayName("replaces the pattern and the matcher, and leaves the rest of java.util.regex")
	void regex() {
		Selection selection = install(new dev.gmitch215.bytebox.regex.Substitutions()).get(0);

		assertTrue(selection.matches("java.util.regex.Pattern"));
		assertTrue(selection.matches("java.util.regex.Matcher"));
		assertFalse(selection.matches("java.util.regex.MatchResult"));
		assertFalse(selection.matches("dev.gmitch215.bytebox.regex.Regex"));
		assertEquals(
			List.of("java.util.regex", "dev.gmitch215.bytebox.regex"),
			selection.replacement()
		);
	}

	@Test
	@DisplayName("supplies the object streams the class library has only markers for")
	void io() {
		Selection selection = install(new dev.gmitch215.bytebox.io.Substitutions()).get(0);

		assertTrue(selection.matches("java.io.ObjectOutputStream"));
		assertTrue(selection.matches("java.io.ObjectInputStream"));
		assertFalse(selection.matches("java.io.Serializable"));
		assertEquals(List.of("java.io", "dev.gmitch215.bytebox.io"), selection.replacement());
	}

	private static List<Selection> install(SubstitutionPolicy policy) {
		List<Selection> selections = new ArrayList<>();
		policy.contribute(predicate -> {
			Selection selection = new Selection(predicate);
			selections.add(selection);
			return selection;
		});
		return selections;
	}

	/** Records what a policy asked for instead of rewriting anything. */
	private static final class Selection implements ClassSubstitutionPolicy {

		private final Predicate<String> predicate;
		private final List<String> replacement = new ArrayList<>();

		Selection(Predicate<String> predicate) {
			this.predicate = predicate;
		}

		boolean matches(String className) {
			return predicate.test(className);
		}

		List<String> replacement() {
			return replacement;
		}

		@Override
		public ClassSubstitutionPolicy replacePackage(String from, String to) {
			replacement.add(from);
			replacement.add(to);
			return this;
		}

		@Override
		public ClassSubstitutionPolicy simpleNamePrefix(String prefix) {
			return this;
		}

		@Override
		public ClassSubstitutionPolicy simpleNameSuffix(String suffix) {
			return this;
		}

		@Override
		public ClassSubstitutionPolicy packagePrefix(String prefix) {
			return this;
		}

		@Override
		public ClassSubstitutionPolicy packageSuffix(String suffix) {
			return this;
		}

		@Override
		public ClassSubstitutionPolicy dontFallbackWhenNoSubstitution() {
			return this;
		}
	}
}
