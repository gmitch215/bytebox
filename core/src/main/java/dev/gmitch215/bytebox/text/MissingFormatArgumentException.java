package dev.gmitch215.bytebox.text;

/**
 * A specifier with no argument to fill it, standing in for
 * {@code java.util.MissingFormatArgumentException}.
 *
 * <p>Absent from the class library, and supplied because a formatter has to throw it.
 *
 * @since 1.0.0
 */
public class MissingFormatArgumentException extends IllegalFormatException {

	private static final long serialVersionUID = 19190115L;

	private final String specifier;

	/**
	 * @param specifier the specifier that had no argument
	 */
	public MissingFormatArgumentException(String specifier) {
		super("Format specifier '" + specifier + "'");
		this.specifier = specifier;
	}

	/** {@return the specifier that had no argument} */
	public String getFormatSpecifier() {
		return specifier;
	}
}
