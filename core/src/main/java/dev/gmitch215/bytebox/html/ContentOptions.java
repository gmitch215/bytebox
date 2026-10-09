package dev.gmitch215.bytebox.html;

import org.teavm.jso.JSObject;

/**
 * How inserted content is read. Without options a string is text and is escaped; with
 * {@link #html()} it is raw HTML.
 *
 * @since 1.0.3
 */
public interface ContentOptions extends JSObject {
	/** {@return options that insert the string as raw HTML} */
	static ContentOptions html() {
		return Hooks.options(true);
	}
}
