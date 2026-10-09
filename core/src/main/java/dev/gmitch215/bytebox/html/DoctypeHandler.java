package dev.gmitch215.bytebox.html;

/**
 * Called for the document's doctype.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface DoctypeHandler {
	/**
	 * Handles the doctype.
	 *
	 * @param doctype the doctype, for use inside this call
	 */
	void doctype(Doctype doctype);
}
