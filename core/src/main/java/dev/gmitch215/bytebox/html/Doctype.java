package dev.gmitch215.bytebox.html;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * The document's doctype, such as {@code <!DOCTYPE html>}.
 *
 * @since 1.0.3
 */
public interface Doctype extends JSObject {
	/** {@return the name, or {@code null} when the doctype has none} */
	@JSProperty
	String getName();

	/** {@return the quoted string after {@code PUBLIC}, or {@code null}} */
	@JSProperty
	String getPublicId();

	/** {@return the quoted string after {@code SYSTEM} or after the public id, or {@code null}} */
	@JSProperty
	String getSystemId();
}
