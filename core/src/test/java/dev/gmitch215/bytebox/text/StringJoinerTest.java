package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("joining strings")
class StringJoinerTest {

	@Test
	@DisplayName("puts the separator between elements and the affixes around them")
	void joins() {
		StringJoiner joiner = new StringJoiner(", ", "[", "]");
		joiner.add("a").add("b").add("c");

		assertEquals("[a, b, c]", joiner.toString());
		assertEquals(9, joiner.length());
	}

	@Test
	@DisplayName("answers the affixes when nothing was added, and the empty value once one is set")
	void empty() {
		assertEquals("", new StringJoiner(",").toString());
		assertEquals("<>", new StringJoiner(",", "<", ">").toString());
		assertEquals("none", new StringJoiner(",", "<", ">").setEmptyValue("none").toString());
		assertEquals(4, new StringJoiner(",").setEmptyValue("none").length());
	}

	@Test
	@DisplayName("keeps the empty value only while nothing has been added")
	void emptyValueGivesWayToContent() {
		StringJoiner joiner = new StringJoiner(",", "[", "]").setEmptyValue("none");

		assertEquals("none", joiner.toString());
		joiner.add("a");
		assertEquals("[a]", joiner.toString());
	}

	@Test
	@DisplayName("takes another joiner's elements without its affixes")
	void merges() {
		StringJoiner joiner = new StringJoiner("-", "<", ">");
		joiner.merge(new StringJoiner(",", "(", ")").add("x").add("y"));

		assertEquals("<x,y>", joiner.toString());
	}

	@Test
	@DisplayName("a merge of an empty joiner adds nothing, not a separator")
	void mergesNothing() {
		StringJoiner joiner = new StringJoiner("-").add("a");
		joiner.merge(new StringJoiner(","));

		assertEquals("a", joiner.toString());
	}

	@Test
	@DisplayName("refuses a null separator or affix, which is where a JVM refuses too")
	void refusesNull() {
		assertThrows(NullPointerException.class, () -> new StringJoiner(null));
		assertThrows(NullPointerException.class, () -> new StringJoiner(",", null, ")"));
		assertThrows(NullPointerException.class, () -> new StringJoiner(",").setEmptyValue(null));
	}

	@Test
	@DisplayName("adds a null element as the four characters a JVM writes")
	void addsNull() {
		assertEquals("null,a", new StringJoiner(",").add(null).add("a").toString());
	}
}
