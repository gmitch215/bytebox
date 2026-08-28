package dev.gmitch215.bytebox.builtin;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.Bytes;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.ArrayBufferView;

/**
 * The platform's Web Crypto, exposed as itself.
 *
 * <p>Hashing and signing run in the runtime rather than in the binary, so a digest costs no wasm
 * bytes and runs at native speed. Every method here blocks, because Web Crypto is asynchronous.
 *
 * {@snippet lang = "java":
 * String digest = Text.toHex(Crypto.sha256("hello world!"));
 *}
 *
 * @since 1.0.0
 */
public final class Crypto {

	private Crypto() {}

	/**
	 * MD5 of text, UTF-8 encoded.
	 *
	 * <p>Not part of Web Crypto. Cloudflare supports it for {@code digest} only, for talking to systems
	 * that will not change, and states plainly that it is weak. Nothing security-bearing should rest on
	 * it, and it is not available as an HMAC hash.
	 *
	 * @param text the text
	 * @return the digest
	 */
	public static ArrayBuffer md5(String text) {
		return Async.await(digestText("MD5", text));
	}

	/**
	 * SHA-256 of text, UTF-8 encoded.
	 *
	 * @param text the text
	 * @return the digest
	 */
	public static ArrayBuffer sha256(String text) {
		return Async.await(digestText("SHA-256", text));
	}

	/**
	 * SHA-512 of text, UTF-8 encoded.
	 *
	 * @param text the text
	 * @return the digest
	 */
	public static ArrayBuffer sha512(String text) {
		return Async.await(digestText("SHA-512", text));
	}

	/**
	 * SHA-1 of text, UTF-8 encoded.
	 *
	 * <p>Broken for anything adversarial. Present because two things still need it: an interoperable
	 * {@code serialVersionUID}, which the serialization specification fixes to SHA-1, and older
	 * protocols that will not change.
	 *
	 * @param text the text
	 * @return the digest
	 */
	public static ArrayBuffer sha1(String text) {
		return Async.await(digestText("SHA-1", text));
	}

	/**
	 * Hashes bytes with a named algorithm.
	 *
	 * @param algorithm {@code SHA-1}, {@code SHA-256}, {@code SHA-384}, {@code SHA-512} or
	 *     {@code MD5}
	 * @param bytes the bytes
	 * @return the digest
	 */
	public static ArrayBuffer digest(String algorithm, ArrayBuffer bytes) {
		return Async.await(digestBytes(algorithm, bytes));
	}

	/**
	 * Hashes bytes with a named algorithm.
	 *
	 * @param algorithm {@code SHA-1}, {@code SHA-256}, {@code SHA-384}, {@code SHA-512} or
	 *     {@code MD5}
	 * @param bytes the bytes
	 * @return the digest
	 */
	public static ArrayBuffer digest(String algorithm, byte[] bytes) {
		return digest(algorithm, Bytes.toBuffer(bytes));
	}

	/**
	 * MD5 of bytes.
	 *
	 * @param bytes the bytes
	 * @return the digest
	 */
	public static ArrayBuffer md5(byte[] bytes) {
		return digest("MD5", bytes);
	}

	/**
	 * SHA-1 of bytes.
	 *
	 * @param bytes the bytes
	 * @return the digest
	 */
	public static ArrayBuffer sha1(byte[] bytes) {
		return digest("SHA-1", bytes);
	}

	/**
	 * SHA-256 of bytes.
	 *
	 * @param bytes the bytes
	 * @return the digest
	 */
	public static ArrayBuffer sha256(byte[] bytes) {
		return digest("SHA-256", bytes);
	}

	/**
	 * SHA-512 of bytes.
	 *
	 * @param bytes the bytes
	 * @return the digest
	 */
	public static ArrayBuffer sha512(byte[] bytes) {
		return digest("SHA-512", bytes);
	}

	/**
	 * HMAC of text under a key.
	 *
	 * @param algorithm {@code SHA-256} or another SHA variant. MD5 is refused here
	 * @param key the key
	 * @param text the text
	 * @return the signature
	 */
	public static ArrayBuffer hmac(String algorithm, String key, String text) {
		return Async.await(signHmac(algorithm, key, text));
	}

	/**
	 * HMAC of bytes under a key of bytes.
	 *
	 * <p>The form to use for a key that is not text. A key given as a {@code String} is UTF-8 encoded,
	 * which is wrong for a key that came out of a key derivation or a random generator.
	 *
	 * @param algorithm {@code SHA-256} or another SHA variant. MD5 is refused here
	 * @param key the key
	 * @param data the bytes to sign
	 * @return the signature
	 */
	public static ArrayBuffer hmac(String algorithm, byte[] key, byte[] data) {
		return Async.await(signHmacBytes(algorithm, Bytes.toBuffer(key), Bytes.toBuffer(data)));
	}

	/**
	 * HMAC-SHA256 of text under a key.
	 *
	 * @param key {@code String} key
	 * @param text {@code String} text
	 * @return {@code ArrayBuffer} signature
	 */
	public static ArrayBuffer hmacSha256(String key, String text) {
		return hmac("SHA-256", key, text);
	}

	/**
	 * Compares two strings without leaking which byte differed through timing.
	 *
	 * <p>An ordinary {@code equals} returns as soon as it finds a difference, which tells an attacker
	 * how much of a guess was right. This does not.
	 *
	 * @param left the first string
	 * @param right the second string
	 * @return whether they match
	 */
	public static boolean timingSafeEquals(String left, String right) {
		return timingSafeEqualViews(Text.encode(left), Text.encode(right));
	}

	/**
	 * Compares two byte arrays without leaking which byte differed through timing.
	 *
	 * @param left the first bytes
	 * @param right the second bytes
	 * @return whether they match
	 */
	public static boolean timingSafeEquals(byte[] left, byte[] right) {
		return timingSafeEqualViews(Bytes.toUnsignedView(left), Bytes.toUnsignedView(right));
	}

	/**
	 * Both spellings, then a constant-time comparison of our own.
	 *
	 * <p>The runtime's own comparison is a non-standard extension, and where it lives has moved: the
	 * generated types put it on {@code crypto.subtle} and the documentation puts it on {@code crypto}.
	 * Trying both costs a property read. The loop is the answer for a runtime that has neither, and it
	 * reads every byte rather than returning at the first difference, which is the whole point.
	 *
	 * <p>Unequal lengths return false before any of that. The runtime's version throws on a length
	 * mismatch, and a length was never the secret.
	 */
	@JSBody(
		params = { "left", "right" },
		script = "if (left.byteLength !== right.byteLength) return false;" +
			" if (crypto.subtle && typeof crypto.subtle.timingSafeEqual === 'function')" +
			"   return crypto.subtle.timingSafeEqual(left, right);" +
			" if (typeof crypto.timingSafeEqual === 'function')" +
			"   return crypto.timingSafeEqual(left, right);" +
			" var mismatch = 0;" +
			" for (var i = 0; i < left.length; i++) mismatch |= left[i] ^ right[i];" +
			" return mismatch === 0;"
	)
	private static native boolean timingSafeEqualViews(ArrayBufferView left, ArrayBufferView right);

	/** {@return a cryptographically random UUID} */
	@JSBody(script = "return crypto.randomUUID();")
	public static native String uuid();

	/**
	 * Cryptographically random bytes.
	 *
	 * @param count how many
	 * @return the bytes
	 */
	@JSBody(
		params = "count",
		script = "return crypto.getRandomValues(new Uint8Array(count)).buffer;"
	)
	public static native ArrayBuffer random(int count);

	/**
	 * Cryptographically random 32 bytes.
	 *
	 * @return the bytes
	 */
	public static ArrayBuffer random32() {
		return random(32);
	}

	@JSBody(
		params = { "algorithm", "text" },
		script = "return crypto.subtle.digest(algorithm, new TextEncoder().encode(text));"
	)
	private static native JSPromise<ArrayBuffer> digestText(String algorithm, String text);

	@JSBody(
		params = { "algorithm", "bytes" },
		script = "return crypto.subtle.digest(algorithm, bytes);"
	)
	private static native JSPromise<ArrayBuffer> digestBytes(String algorithm, ArrayBuffer bytes);

	@JSBody(
		params = { "algorithm", "key", "text" },
		script = "var encoder = new TextEncoder();" +
			" return crypto.subtle.importKey('raw', encoder.encode(key)," +
			"   { name: 'HMAC', hash: algorithm }, false, ['sign'])" +
			"   .then(function (k) {" +
			"     return crypto.subtle.sign('HMAC', k, encoder.encode(text));" +
			"   });"
	)
	private static native JSPromise<ArrayBuffer> signHmac(
		String algorithm,
		String key,
		String text
	);

	@JSBody(
		params = { "algorithm", "key", "data" },
		script = "return crypto.subtle.importKey('raw', key," +
			"   { name: 'HMAC', hash: algorithm }, false, ['sign'])" +
			"   .then(function (k) {" +
			"     return crypto.subtle.sign('HMAC', k, data);" +
			"   });"
	)
	private static native JSPromise<ArrayBuffer> signHmacBytes(
		String algorithm,
		ArrayBuffer key,
		ArrayBuffer data
	);
}
