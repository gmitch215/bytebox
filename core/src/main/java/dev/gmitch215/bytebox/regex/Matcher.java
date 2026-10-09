package dev.gmitch215.bytebox.regex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.stream.Stream;

/**
 * A match in progress, standing in for {@code java.util.regex.Matcher}.
 *
 * <p>The compiler substitutes {@code java.util.regex.Matcher} for this one. Finding is the platform's;
 * the replacement syntax is not, because Java writes {@code $1} and {@code ${name}} where the platform
 * writes {@code $1} and {@code $<name>}, so every replacement is assembled here rather than handed over.
 *
 * <p>What is not here, because it has no equivalent and a wrong answer would be worse than a build
 * failure: a search region, {@code hitEnd} and {@code requireEnd}. A program reaching for one fails to
 * compile.
 *
 * @since 1.0.0
 */
public final class Matcher implements MatchResult {

	private final Pattern parent;
	private final String input;

	private final Engine engine;

	/** The source this runs, which depends on whether the input has a character above U+FFFF. */
	private final Pattern.Form form;

	private Object match;
	private int from;
	private boolean matched;

	/** Where the text the match was found in begins, which is not the start of the input after a try inside a pair. */
	private int shift;

	Matcher(Pattern parent, String input) {
		this.parent = parent;
		this.engine = parent.engine();
		this.input = input;
		this.form = parent.form(input);
	}

	/** {@return the pattern this matches with} */
	public Pattern pattern() {
		return parent;
	}

	/**
	 * Finds the next match after where the last one ended.
	 *
	 * @return whether one was found
	 */
	public boolean find() {
		if (from > input.length()) {
			matched = false;
			return false;
		}
		shift = 0;
		match = search(from);
		matched = match != null;
		if (!matched) {
			from = input.length() + 1;
			return false;
		}
		int end = position(0, true);
		// a match of nothing has to move on, or the search would find it again for ever
		from = end == position(0, false) ? end + 1 : end;
		return true;
	}

	/**
	 * The next match from a position, as a JVM finds it.
	 *
	 * <p>A JVM looks at every UTF-16 unit, the middle of a surrogate pair included: a search resumes one
	 * unit after an empty match, and a pattern that cannot take half of a pair tries each unit in turn. A
	 * pattern for the {@code v} flag reads code points. The platform backs up to the start of a pair
	 * it is given the middle of, and when its own scan reaches the middle of one it reads nothing there
	 * ({@code (?![\s\S])} holds), so what it finds starting there is not what a JVM finds. Every try in
	 * the middle of a pair is made here instead, on the text from there, where nothing before it can be
	 * seen, which is what a JVM sees too.
	 */
	private Object search(int at) {
		if (!form.unicode()) return engine.find(form.searching(), input, at);
		if (insidePair(at)) {
			Object inside = tryInside(at);
			if (inside != null) return inside;
			at++;
		}
		while (true) {
			Object found = engine.find(form.searching(), input, at);
			int limit = found == null ? input.length() : engine.start(found, 0);
			if (form.unitScan()) {
				for (int index = at + 1; index < limit; index++) {
					if (!insidePair(index)) continue;
					Object inside = tryInside(index);
					if (inside != null) return inside;
				}
			}
			if (found == null || !insidePair(limit)) return found;
			// a match that starts in the middle of a pair was read by the platform without that half
			if (form.unitScan()) {
				Object inside = tryInside(limit);
				if (inside != null) return inside;
			}
			at = limit + 1;
		}
	}

	private boolean insidePair(int index) {
		return (
			index > 0 &&
			index < input.length() &&
			Character.isLowSurrogate(input.charAt(index)) &&
			Character.isHighSurrogate(input.charAt(index - 1))
		);
	}

	/** A match that starts at the second half of a pair, found on the text from there. */
	private Object tryInside(int index) {
		Object inside = engine.find(form.inside(), input.substring(index), 0);
		if (inside != null) shift = index;
		return inside;
	}

	/** Where the whole match, or a group, begins or ends in the input, or -1 when it took no part. */
	private int position(int group, boolean end) {
		return shifted(end ? engine.end(match, group) : engine.start(match, group));
	}

	private int shifted(int at) {
		return at < 0 ? at : at + shift;
	}

	/**
	 * Finds the next match from a position, forgetting where the last one ended.
	 *
	 * @param at where to start
	 * @return whether one was found
	 */
	public boolean find(int at) {
		if (at < 0 || at > input.length()) {
			throw new IndexOutOfBoundsException("no position " + at + " in the input");
		}
		reset();
		from = at;
		return find();
	}

	/**
	 * Whether the whole input matches.
	 *
	 * @return whether it does
	 */
	public boolean matches() {
		shift = 0;
		match = engine.find(form.anchored(), input, 0);
		matched = match != null;
		return matched;
	}

	/**
	 * Whether the start of the input matches.
	 *
	 * @return whether it does
	 */
	public boolean lookingAt() {
		shift = 0;
		match = engine.find(form.leading(), input, 0);
		matched = match != null;
		return matched;
	}

	/**
	 * Forgets everything found so far.
	 *
	 * @return this matcher
	 */
	public Matcher reset() {
		match = null;
		matched = false;
		from = 0;
		return this;
	}

	/**
	 * Forgets everything found so far and takes a new input.
	 *
	 * @param input what to search
	 * @return this matcher
	 */
	public Matcher reset(CharSequence input) {
		return new Matcher(parent, input.toString());
	}

	@Override
	public String group() {
		return group(0);
	}

	@Override
	public String group(int index) {
		checkMatched();
		checkGroup(index);
		return engine.group(match, jsGroup(index));
	}

	@Override
	public String group(String name) {
		checkMatched();
		checkName(name);
		return engine.named(match, name);
	}

	@Override
	public int groupCount() {
		return parent.groups().count();
	}

	@Override
	public int start() {
		return start(0);
	}

	@Override
	public int start(int index) {
		checkMatched();
		checkGroup(index);
		return position(jsGroup(index), false);
	}

	@Override
	public int start(String name) {
		checkMatched();
		checkName(name);
		return shifted(engine.namedStart(match, name));
	}

	@Override
	public int end() {
		return end(0);
	}

	@Override
	public int end(int index) {
		checkMatched();
		checkGroup(index);
		return position(jsGroup(index), true);
	}

	@Override
	public int end(String name) {
		checkMatched();
		checkName(name);
		return shifted(engine.namedEnd(match, name));
	}

	@Override
	public Map<String, Integer> namedGroups() {
		return parent.namedGroups();
	}

	/**
	 * Every match, as results that keep what they found.
	 *
	 * @return the matches
	 */
	public Stream<MatchResult> results() {
		List<MatchResult> found = new ArrayList<>();
		Matcher walker = new Matcher(parent, input);
		while (walker.find()) found.add(walker.toMatchResult());
		return found.stream();
	}

	/** {@return what was found, kept as it is now} */
	public MatchResult toMatchResult() {
		checkMatched();
		int count = groupCount();
		List<String> groups = new ArrayList<>(count + 1);
		List<Integer> starts = new ArrayList<>(count + 1);
		List<Integer> ends = new ArrayList<>(count + 1);
		for (int index = 0; index <= count; index++) {
			groups.add(engine.group(match, jsGroup(index)));
			starts.add(position(jsGroup(index), false));
			ends.add(position(jsGroup(index), true));
		}
		Map<String, Integer> named = parent.namedGroups();
		return new Kept(groups, starts, ends, named);
	}

	/**
	 * Replaces every match.
	 *
	 * @param replacement the replacement, where {@code $1} and {@code ${name}} stand for groups
	 * @return the result
	 */
	public String replaceAll(String replacement) {
		return replace(replacement, Integer.MAX_VALUE);
	}

	/**
	 * Replaces the first match.
	 *
	 * @param replacement the replacement, where {@code $1} and {@code ${name}} stand for groups
	 * @return the result
	 */
	public String replaceFirst(String replacement) {
		return replace(replacement, 1);
	}

	/**
	 * Appends everything up to the current match, then the replacement.
	 *
	 * @param into where to append
	 * @param replacement the replacement, where {@code $1} and {@code ${name}} stand for groups
	 * @return this matcher
	 */
	public Matcher appendReplacement(StringBuilder into, String replacement) {
		checkMatched();
		into.append(input, appendFrom, start());
		expand(into, replacement);
		appendFrom = end();
		return this;
	}

	/**
	 * Appends everything after the last match.
	 *
	 * @param into where to append
	 * @return what was appended to
	 */
	public StringBuilder appendTail(StringBuilder into) {
		into.append(input, appendFrom, input.length());
		return into;
	}

	/**
	 * A replacement that stands for itself, with nothing in it taken as a group.
	 *
	 * @param text the replacement
	 * @return the escaped replacement
	 */
	public static String quoteReplacement(String text) {
		StringBuilder escaped = new StringBuilder();
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character == '\\' || character == '$') escaped.append('\\');
			escaped.append(character);
		}
		return escaped.toString();
	}

	// #region replacement

	private int appendFrom;

	private String replace(String replacement, int most) {
		StringBuilder into = new StringBuilder();
		Matcher walker = new Matcher(parent, input);
		int done = 0;
		int at = 0;
		while (done < most && walker.find()) {
			into.append(input, at, walker.start());
			walker.expand(into, replacement);
			at = walker.end();
			done++;
		}
		into.append(input, at, input.length());
		return into.toString();
	}

	/**
	 * The replacement with its group references filled in.
	 *
	 * <p>Java reads a run of digits after {@code $} as far as it names a group, so {@code $12} is group
	 * 12 when there are twelve and group 1 followed by a {@code 2} when there are not.
	 */
	private void expand(StringBuilder into, String replacement) {
		int at = 0;
		while (at < replacement.length()) {
			char character = replacement.charAt(at);
			if (character == '\\') {
				at++;
				if (at >= replacement.length()) {
					throw new IllegalArgumentException("the replacement ends with a backslash");
				}
				into.append(replacement.charAt(at++));
				continue;
			}
			if (character != '$') {
				into.append(character);
				at++;
				continue;
			}

			at++;
			if (at >= replacement.length()) {
				throw new IllegalArgumentException("the replacement ends with a dollar sign");
			}
			if (replacement.charAt(at) == '{') {
				int close = replacement.indexOf('}', at);
				if (close < 0) {
					throw new IllegalArgumentException(
						"the replacement has an unclosed group name"
					);
				}
				String name = replacement.substring(at + 1, close);
				checkName(name);
				String value = engine.named(match, name);
				if (value != null) into.append(value);
				at = close + 1;
				continue;
			}
			if (!isDigit(replacement.charAt(at))) {
				throw new IllegalArgumentException(
					"a dollar sign in a replacement is followed by a group"
				);
			}
			// the first digit has to name a group; the ones after it are taken only while they still do
			int count = groupCount();
			int group = replacement.charAt(at++) - '0';
			if (group > count) throw new IndexOutOfBoundsException("No group " + group);
			while (at < replacement.length() && isDigit(replacement.charAt(at))) {
				// a long run of digits would wrap the number negative and slip past the check
				long wider = (long) group * 10 + (replacement.charAt(at) - '0');
				if (wider > count) break;
				group = (int) wider;
				at++;
			}
			String value = engine.group(match, jsGroup(group));
			if (value != null) into.append(value);
		}
	}

	// #endregion

	// #region checks

	private void checkMatched() {
		if (!matched) throw new IllegalStateException("no match has been found yet");
	}

	private void checkGroup(int index) {
		if (index < 0 || index > groupCount()) {
			throw new IndexOutOfBoundsException("no group " + index);
		}
	}

	private void checkName(String name) {
		if (!parent.groups().names.containsKey(name)) {
			throw new IllegalArgumentException("no group called " + name);
		}
	}

	/** The platform's number for a group by the caller's, which an atomic group written as a capture shifts. */
	private int jsGroup(int index) {
		return parent.groups().js(index);
	}

	private static boolean isDigit(char character) {
		return character >= '0' && character <= '9';
	}

	// #endregion

	/** A match kept after the matcher moved on. */
	private static final class Kept implements MatchResult {

		private final List<String> groups;
		private final List<Integer> starts;
		private final List<Integer> ends;
		private final Map<String, Integer> named;

		Kept(
			List<String> groups,
			List<Integer> starts,
			List<Integer> ends,
			Map<String, Integer> named
		) {
			this.groups = groups;
			this.starts = starts;
			this.ends = ends;
			this.named = new LinkedHashMap<>(named);
		}

		@Override
		public String group() {
			return groups.get(0);
		}

		@Override
		public String group(int index) {
			return groups.get(index);
		}

		@Override
		public String group(String name) {
			return groups.get(index(name));
		}

		@Override
		public int groupCount() {
			return groups.size() - 1;
		}

		@Override
		public int start() {
			return starts.get(0);
		}

		@Override
		public int start(int index) {
			return starts.get(index);
		}

		@Override
		public int start(String name) {
			return starts.get(index(name));
		}

		@Override
		public int end() {
			return ends.get(0);
		}

		@Override
		public int end(int index) {
			return ends.get(index);
		}

		@Override
		public int end(String name) {
			return ends.get(index(name));
		}

		@Override
		public Map<String, Integer> namedGroups() {
			return named;
		}

		private int index(String name) {
			Integer at = named.get(name);
			if (at == null) throw new IllegalArgumentException("no group called " + name);
			return at;
		}
	}
}
