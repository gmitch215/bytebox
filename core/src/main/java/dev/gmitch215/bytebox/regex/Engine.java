package dev.gmitch215.bytebox.regex;

/**
 * A regular-expression engine, asked only the things {@link Matcher} cannot work out for itself.
 *
 * <p>{@link Pattern} and {@link Matcher} take this rather than calling the platform directly, so that
 * the parts written here - where a search resumes, how a zero-width match moves on, what a replacement
 * expands to, how a split treats an empty piece - run under test on a JVM against the engine the
 * behaviour is being claimed to match.
 *
 * <p>A compiled expression and a match are whatever the engine makes them, and nothing outside the
 * engine reads them.
 */
interface Engine {
	/**
	 * Compiles an expression.
	 *
	 * @param source the source, already rewritten by {@link Translate}
	 * @param flags case folding, or nothing
	 * @return the compiled expression
	 */
	Object compile(String source, String flags);

	/**
	 * Runs an expression from a position.
	 *
	 * @param expression the compiled expression
	 * @param input what to search
	 * @param from where to start
	 * @return the match, or null when there is none
	 */
	Object find(Object expression, String input, int from);

	/**
	 * @param match a match
	 * @return how many groups it has, counting the whole match as one
	 */
	int count(Object match);

	/**
	 * @param match a match
	 * @param index the group
	 * @return what it matched, or null when it took part in no match
	 */
	String group(Object match, int index);

	/**
	 * @param match a match
	 * @param name the group name
	 * @return what it matched, or null when it took part in no match
	 */
	String named(Object match, String name);

	/**
	 * @param match a match
	 * @param index the group
	 * @return where it started, or -1 when it took part in no match
	 */
	int start(Object match, int index);

	/**
	 * @param match a match
	 * @param index the group
	 * @return where it ended, or -1 when it took part in no match
	 */
	int end(Object match, int index);

	/**
	 * @param match a match
	 * @param name the group name
	 * @return where it started, or -1 when it took part in no match
	 */
	int namedStart(Object match, String name);

	/**
	 * @param match a match
	 * @param name the group name
	 * @return where it ended, or -1 when it took part in no match
	 */
	int namedEnd(Object match, String name);
}
