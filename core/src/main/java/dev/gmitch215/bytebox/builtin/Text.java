package dev.gmitch215.bytebox.builtin;

import dev.gmitch215.bytebox.js.Bytes;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * The platform's own encoders, exposed as themselves.
 *
 * <p>Every charset beyond UTF-8 costs a table in the binary if the class library provides it, and the
 * isolate already has {@code TextDecoder}, which handles every encoding the WHATWG registry lists.
 * Base64 and hexadecimal are here for the same reason: the platform has them and a Java
 * implementation would be bytes spent on something already present.
 *
 * @since 1.0.0
 */
public final class Text {

	private Text() {}

	/**
	 * Encodes text as UTF-8.
	 *
	 * @param text the text
	 * @return the bytes
	 */
	@JSBody(params = "text", script = "return new TextEncoder().encode(text);")
	public static native Uint8Array encode(String text);

	/**
	 * Decodes UTF-8.
	 *
	 * @param bytes the bytes
	 * @return the text
	 */
	@JSBody(params = "bytes", script = "return new TextDecoder().decode(bytes);")
	public static native String decode(ArrayBuffer bytes);

	/**
	 * Decodes bytes in a named encoding.
	 *
	 * @param bytes the bytes
	 * @param encoding a WHATWG label, such as {@code shift_jis} or {@code windows-1252}
	 * @return the text
	 */
	@JSBody(
		params = { "bytes", "encoding" },
		script = "return new TextDecoder(encoding).decode(bytes);"
	)
	public static native String decode(ArrayBuffer bytes, String encoding);

	/**
	 * Decodes UTF-8.
	 *
	 * @param bytes the bytes
	 * @return the text
	 */
	public static String decode(byte[] bytes) {
		return decode(Bytes.toBuffer(bytes));
	}

	/**
	 * Decodes bytes in a named encoding.
	 *
	 * @param bytes the bytes
	 * @param encoding a WHATWG label, such as {@code shift_jis} or {@code windows-1252}
	 * @return the text
	 */
	public static String decode(byte[] bytes, String encoding) {
		return decode(Bytes.toBuffer(bytes), encoding);
	}

	/**
	 * Reports whether the runtime knows an encoding.
	 *
	 * @param encoding a WHATWG label
	 * @return whether a decoder can be built for it
	 */
	@JSBody(
		params = "encoding",
		script = "try { new TextDecoder(encoding); return true; } catch (e) { return false; }"
	)
	public static native boolean supports(String encoding);

	/**
	 * Encodes bytes as base64.
	 *
	 * @param bytes the bytes
	 * @return the base64 text
	 */
	@JSBody(
		params = "bytes",
		script = "const view = new Uint8Array(bytes);" +
			" let binary = '';" +
			" for (let i = 0; i < view.length; i++) binary += String.fromCharCode(view[i]);" +
			" return btoa(binary);"
	)
	public static native String toBase64(ArrayBuffer bytes);

	/**
	 * Encodes bytes as base64.
	 *
	 * @param bytes the bytes
	 * @return the base64 text
	 */
	public static String toBase64(byte[] bytes) {
		return toBase64(Bytes.toBuffer(bytes));
	}

	/**
	 * Decodes base64.
	 *
	 * @param base64 the base64 text
	 * @return the bytes
	 */
	@JSBody(
		params = "base64",
		script = "const binary = atob(base64);" +
			" const bytes = new Uint8Array(binary.length);" +
			" for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);" +
			" return bytes.buffer;"
	)
	public static native ArrayBuffer fromBase64(String base64);

	/**
	 * Encodes bytes as lowercase hexadecimal.
	 *
	 * @param bytes the bytes
	 * @return the hexadecimal text
	 */
	@JSBody(
		params = "bytes",
		script = "return Array.from(new Uint8Array(bytes)," +
			" (b) => b.toString(16).padStart(2, '0')).join('');"
	)
	public static native String toHex(ArrayBuffer bytes);

	/**
	 * Encodes bytes as lowercase hexadecimal.
	 *
	 * @param bytes the bytes
	 * @return the hexadecimal text
	 */
	public static String toHex(byte[] bytes) {
		return toHex(Bytes.toBuffer(bytes));
	}

	/**
	 * Decodes hexadecimal.
	 *
	 * @param hex the hexadecimal text, with an even number of digits
	 * @return the bytes
	 */
	@JSBody(
		params = "hex",
		script = "if (hex.length % 2 !== 0) throw new Error('hexadecimal needs an even number of digits');" +
			" const bytes = new Uint8Array(hex.length / 2);" +
			" for (let i = 0; i < bytes.length; i++)" +
			"   bytes[i] = parseInt(hex.substr(i * 2, 2), 16);" +
			" return bytes.buffer;"
	)
	public static native ArrayBuffer fromHex(String hex);

	/**
	 * Percent-encodes a value for use in a URL.
	 *
	 * @param value the value
	 * @return the encoded value
	 */
	@JSBody(params = "value", script = "return encodeURIComponent(value);")
	public static native String urlEncode(String value);

	/**
	 * Reverses {@link #urlEncode(String)}.
	 *
	 * @param value the encoded value
	 * @return the decoded value
	 */
	@JSBody(params = "value", script = "return decodeURIComponent(value);")
	public static native String urlDecode(String value);
}
