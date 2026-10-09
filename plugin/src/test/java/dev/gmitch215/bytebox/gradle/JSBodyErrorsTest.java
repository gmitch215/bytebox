package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSBody;

@DisplayName("finding a @JSBody script error in compiler output")
class JSBodyErrorsTest {

	@Test
	@DisplayName("returns each error with the method the compiler named under it")
	void errorsWithTheirMethod() {
		JSBodyErrors.Collector collector = new JSBodyErrors.Collector();
		collector.onOutput("> Task :generateWasmGC\n");
		collector.onOutput("Error in @JSBody script line 0, char 23: syntax error\n");
		collector.onOutput("    at fixture.Handler.broken\n");
		collector.onOutput("Error in @JSBody script line 0, char 24: missing ) in parenthetical\n");
		collector.onOutput("    at fixture.Handler.broken\n");

		assertEquals(
			List.of(
				"Error in @JSBody script line 0, char 23: syntax error\n    at fixture.Handler.broken",
				"Error in @JSBody script line 0, char 24: missing ) in parenthetical\n" +
					"    at fixture.Handler.broken"
			),
			collector.errors()
		);
	}

	@Test
	@DisplayName("joins output that arrives in pieces ending anywhere")
	void piecesOfALine() {
		JSBodyErrors.Collector collector = new JSBodyErrors.Collector();
		collector.onOutput("Error in @JSBo");
		collector.onOutput("dy script line 0, char 4: syntax error\n  ");
		collector.onOutput("  at fixture.Handler.broken");

		assertEquals(
			List.of(
				"Error in @JSBody script line 0, char 4: syntax error\n    at fixture.Handler.broken"
			),
			collector.errors()
		);
	}

	@Test
	@DisplayName("keeps an error that names no method")
	void noLocation() {
		JSBodyErrors.Collector collector = new JSBodyErrors.Collector();
		collector.onOutput("Error in @JSBody script line 0, char 4: syntax error\n");
		collector.onOutput("BUILD SUCCESSFUL\n");

		assertEquals(
			List.of("Error in @JSBody script line 0, char 4: syntax error"),
			collector.errors()
		);
	}

	static final class Scripts {

		@JSBody(script = "return (1 +;")
		static native int broken();

		@JSBody(script = "var total = 0; for (const n of [1, 2, 3]) { total += n; } return total;")
		static native int forOf();

		@JSBody(script = "var big = 10n ** 3n; return Number(big);")
		static native int bigint();

		@JSBody(params = { "a" }, script = "let a = 1; return a;")
		static native int shadowsItsParameter();

		@JSBody(params = { "a", "b" }, script = "return a + b;")
		static native int fine(int a, int b);
	}

	private static File location(Class<?> type) throws URISyntaxException {
		return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private static List<File> classpath() throws URISyntaxException {
		return List.of(location(JSBodyErrorsTest.class), location(JSBody.class));
	}

	private static String error(String method) {
		return (
			"Error in @JSBody script line 0, char 4: syntax error\n    at " +
			Scripts.class.getName() +
			"." +
			method
		);
	}

	private static List<String> refused(String... methods) throws URISyntaxException {
		File node = Runner.which("node");
		assertNotNull(node, "these tests need node on PATH");
		List<String> errors = Stream.of(methods).map(JSBodyErrorsTest::error).toList();
		return JSBodyErrors.refusedByV8(errors, classpath(), node);
	}

	@Test
	@DisplayName("keeps an error for a script V8 refuses too, and says what V8 reported")
	void keepsWhatV8Refuses() throws URISyntaxException {
		List<String> kept = refused("broken");

		assertEquals(1, kept.size());
		assertTrue(kept.get(0).startsWith(error("broken")), kept.get(0));
		assertTrue(kept.get(0).contains("\n    V8: "), kept.get(0));
	}

	@Test
	@DisplayName("drops an error for a script V8 reads, for...of and a BigInt literal included")
	void dropsWhatV8Reads() throws URISyntaxException {
		assertEquals(List.of(), refused("forOf", "bigint", "fine"));
	}

	@Test
	@DisplayName("keeps only the refused script when both kinds were reported")
	void keepsOnlyTheRefusedOne() throws URISyntaxException {
		List<String> kept = refused("forOf", "broken", "bigint");

		assertEquals(1, kept.size());
		assertTrue(kept.get(0).startsWith(error("broken")), kept.get(0));
	}

	@Test
	@DisplayName("checks a script with the parameter names it declares")
	void usesTheDeclaredParameters() throws URISyntaxException {
		assertEquals(1, refused("shadowsItsParameter").size());
	}

	@Test
	@DisplayName("keeps an error it cannot trace to a script: no method, no class, no node")
	void keepsWhatItCannotCheck() throws URISyntaxException {
		File node = Runner.which("node");
		assertNotNull(node, "these tests need node on PATH");
		String anonymous = "Error in @JSBody script line 0, char 4: syntax error";
		String missing = anonymous + "\n    at com.example.Gone.broken";
		String noScript = error("notNative");

		assertEquals(
			List.of(anonymous, missing, noScript),
			JSBodyErrors.refusedByV8(List.of(anonymous, missing, noScript), classpath(), node)
		);
		assertEquals(
			List.of(error("forOf")),
			JSBodyErrors.refusedByV8(List.of(error("forOf")), classpath(), null)
		);
	}

	@Test
	@DisplayName("finds nothing in output without one, a warning about a script included")
	void nothingToFind() {
		JSBodyErrors.Collector collector = new JSBodyErrors.Collector();
		collector.onOutput("Warning in @JSBody script line 0, char 4: trailing comma\n");
		collector.onOutput("BUILD SUCCESSFUL in 2s\n");

		assertTrue(collector.errors().isEmpty());
	}
}
