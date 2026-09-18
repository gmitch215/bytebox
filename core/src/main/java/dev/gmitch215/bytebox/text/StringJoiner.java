package dev.gmitch215.bytebox.text;

/**
 * {@code java.util.StringJoiner}, which the class library does not carry.
 *
 * <p>Small, and it blocks more than its size suggests: it is what a good deal of ordinary joining
 * code reaches for, so a library using it does not link at all without this.
 *
 * @since 1.0.2
 */
public final class StringJoiner {

	private final String separator;
	private final String prefix;
	private final String suffix;
	private StringBuilder built;
	private String empty;

	/**
	 * @param separator what goes between elements
	 */
	public StringJoiner(CharSequence separator) {
		this(separator, "", "");
	}

	/**
	 * @param separator what goes between elements
	 * @param prefix what goes before the first
	 * @param suffix what goes after the last
	 */
	public StringJoiner(CharSequence separator, CharSequence prefix, CharSequence suffix) {
		if (separator == null || prefix == null || suffix == null) {
			throw new NullPointerException("separator, prefix and suffix are all required");
		}
		this.separator = separator.toString();
		this.prefix = prefix.toString();
		this.suffix = suffix.toString();
	}

	/**
	 * @param newElement the element to add
	 * @return this joiner
	 */
	public StringJoiner add(CharSequence newElement) {
		prepare().append(newElement);
		return this;
	}

	/**
	 * @param emptyValue what to answer when nothing was added
	 * @return this joiner
	 */
	public StringJoiner setEmptyValue(CharSequence emptyValue) {
		if (emptyValue == null) throw new NullPointerException("emptyValue");
		empty = emptyValue.toString();
		return this;
	}

	/**
	 * @param other the joiner to append, without its prefix or suffix
	 * @return this joiner
	 */
	public StringJoiner merge(StringJoiner other) {
		// from past the other prefix: a merge takes the elements, not the brackets around them
		if (other.built != null) prepare().append(
			other.built,
			other.prefix.length(),
			other.built.length()
		);
		return this;
	}

	/** {@return how long the result would be} */
	public int length() {
		return built == null
			? empty == null
				? prefix.length() + suffix.length()
				: empty.length()
			: built.length() + suffix.length();
	}

	@Override
	public String toString() {
		if (built == null) return empty == null ? prefix + suffix : empty;
		return built + suffix;
	}

	private StringBuilder prepare() {
		if (built == null) built = new StringBuilder().append(prefix);
		else built.append(separator);
		return built;
	}
}
