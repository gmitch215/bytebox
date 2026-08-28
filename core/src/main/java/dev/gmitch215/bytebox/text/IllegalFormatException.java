package dev.gmitch215.bytebox.text;

/**
 * A format string the formatter will not accept, standing in for
 * {@code java.util.IllegalFormatException}.
 *
 * <p>Replaced rather than supplied, and for one reason: the class library's version has
 * package-private constructors, so the two subclasses it is missing cannot be written anywhere else.
 * Its own subclasses keep working, because the compiler rewrites their superclass to this one and the
 * constructors they call are here.
 *
 * @since 1.0.0
 */
public class IllegalFormatException extends IllegalArgumentException {

	private static final long serialVersionUID = 18830826L;

	/** With no message. */
	public IllegalFormatException() {}

	/**
	 * @param message what is wrong with the format string
	 */
	public IllegalFormatException(String message) {
		super(message);
	}
}
