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
	private Object match;
	private int from;
	private boolean matched;

	Matcher(Pattern parent, String input) {
		this.parent = parent;
		this.engine = parent.engine();
		this.input = input;
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
		match = engine.find(parent.searching(), input, from);
		matched = match != null;
		if (!matched) {
			from = input.length() + 1;
			return false;
		}
		int end = engine.end(match, 0);
		// a match of nothing has to move on, or the search would find it again for ever
		from = end == engine.start(match, 0) ? end + 1 : end;
		return true;
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
		match = engine.find(parent.anchored(), input, 0);
		matched = match != null;
		return matched;
	}

	/**
	 * Whether the start of the input matches.
	 *
	 * @return whether it does
	 */
	public boolean lookingAt() {
		match = engine.find(parent.leading(), input, 0);
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
		return engine.group(match, index);
	}

	@Override
	public String group(String name) {
		checkMatched();
		checkName(name);
		return engine.named(match, name);
	}

	@Override
	public int groupCount() {
		return match != null
			? engine.count(match) - 1
			: parent.names().size() > 0
				? parent.names().size()
				: countGroups();
	}

	@Override
	public int start() {
		return start(0);
	}

	@Override
	public int start(int index) {
		checkMatched();
		checkGroup(index);
		return engine.start(match, index);
	}

	@Override
	public int start(String name) {
		checkMatched();
		checkName(name);
		return engine.namedStart(match, name);
	}

	@Override
	public int end() {
		return end(0);
	}

	@Override
	public int end(int index) {
		checkMatched();
		checkGroup(index);
		return engine.end(match, index);
	}

	@Override
	public int end(String name) {
		checkMatched();
		checkName(name);
		return engine.namedEnd(match, name);
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
		int count = engine.count(match) - 1;
		List<String> groups = new ArrayList<>(count + 1);
		List<Integer> starts = new ArrayList<>(count + 1);
		List<Integer> ends = new ArrayList<>(count + 1);
		for (int index = 0; index <= count; index++) {
			groups.add(engine.group(match, index));
			starts.add(engine.start(match, index));
			ends.add(engine.end(match, index));
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
			String value = engine.group(match, group);
			if (value != null) into.append(value);
		}
	}

	// #endregion

	// #region checks

	private void checkMatched() {
		if (!matched) throw new IllegalStateException("no match has been found yet");
	}

	private void checkGroup(int index) {
		int count = engine.count(match) - 1;
		if (index < 0 || index > count) {
			throw new IndexOutOfBoundsException("no group " + index);
		}
	}

	private void checkName(String name) {
		if (!parent.names().contains(name)) {
			throw new IllegalArgumentException("no group called " + name);
		}
	}

	/** Counted from the pattern when nothing has matched yet, which is when the runtime still answers. */
	private int countGroups() {
		return parent.namedGroups().size();
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
