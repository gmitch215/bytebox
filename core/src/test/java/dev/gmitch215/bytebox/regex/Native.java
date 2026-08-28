package dev.gmitch215.bytebox.regex;

/**
 * The engine a JVM has, driven the way the platform's is driven.
 *
 * <p>What this makes checkable is the half that is written here rather than delegated: where a search
 * resumes, how a match of nothing moves on, what a replacement expands to, how a split treats an empty
 * piece. It runs the translated source, so a translation that means the wrong thing shows up as a
 * different answer from the same pattern run directly.
 *
 * <p>The one thing it cannot check is whether the platform's engine and a JVM's agree about the syntax
 * they share, which no test on a JVM can.
 */
final class Native implements Engine {

	static final Native ENGINE = new Native();

	private Native() {}

	@Override
	public Object compile(String source, String flags) {
		int mask = flags.indexOf('i') >= 0 ? java.util.regex.Pattern.CASE_INSENSITIVE : 0;
		return java.util.regex.Pattern.compile(source, mask);
	}

	/**
	 * The platform resumes a search from a position with the whole input still in view, which is what a
	 * matcher over the whole input and {@code find(from)} does.
	 */
	@Override
	public Object find(Object expression, String input, int from) {
		java.util.regex.Matcher matcher = ((java.util.regex.Pattern) expression).matcher(input);
		return matcher.find(from) ? matcher : null;
	}

	@Override
	public int count(Object match) {
		return ((java.util.regex.Matcher) match).groupCount() + 1;
	}

	@Override
	public String group(Object match, int index) {
		return ((java.util.regex.Matcher) match).group(index);
	}

	@Override
	public String named(Object match, String name) {
		return ((java.util.regex.Matcher) match).group(name);
	}

	@Override
	public int start(Object match, int index) {
		return ((java.util.regex.Matcher) match).start(index);
	}

	@Override
	public int end(Object match, int index) {
		return ((java.util.regex.Matcher) match).end(index);
	}

	@Override
	public int namedStart(Object match, String name) {
		return ((java.util.regex.Matcher) match).start(name);
	}

	@Override
	public int namedEnd(Object match, String name) {
		return ((java.util.regex.Matcher) match).end(name);
	}
}
