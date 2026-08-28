package dev.gmitch215.bytebox.js;

import java.util.Objects;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.ArrayBufferView;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Converts between a Java {@code byte[]} and the runtime's buffers.
 *
 * <p>Every platform API that carries bytes carries an {@code ArrayBuffer}, because that is what the
 * runtime speaks. Java code holds a {@code byte[]}. This is the seam, and the methods that take bytes
 * accept both so most code never needs it.
 *
 * {@snippet lang = "java":
 * byte[] digest = Bytes.fromBuffer(Crypto.sha256("hello"));
 * String base64 = Text.toBase64(digest);
 * byte[] raw = Bytes.fromBuffer(Text.fromBase64(base64));
 *}
 *
 * <p>Both directions copy. A WebAssembly garbage-collected array and a JavaScript buffer are separate
 * objects in separate heaps, so there is no view of one that is also the other, and a conversion is
 * proportional to the length rather than free.
 *
 * @since 1.0.0
 */
public final class Bytes {

	private Bytes() {}

	/**
	 * A Java array as a buffer.
	 *
	 * @param bytes the bytes
	 * @return a buffer holding a copy of them
	 */
	public static ArrayBuffer toBuffer(byte[] bytes) {
		return toView(bytes).getBuffer();
	}

	/**
	 * A Java array as a signed byte view.
	 *
	 * <p>The view a JavaScript API wants when it takes a {@code TypedArray} rather than a buffer.
	 * {@link #toUnsignedView} is the same bytes read as unsigned, which is what most of them document.
	 *
	 * @param bytes the bytes
	 * @return a view over a copy of them
	 */
	public static Int8Array toView(byte[] bytes) {
		Objects.requireNonNull(bytes, "bytes");
		return Int8Array.copyFromJavaArray(bytes);
	}

	/**
	 * A Java array as an unsigned byte view.
	 *
	 * <p>The same bytes as {@link #toView}: the difference is how JavaScript reads element 0x80, not
	 * what is stored. Java has no unsigned byte, so a value above 127 arrives negative and comes back
	 * negative.
	 *
	 * @param bytes the bytes
	 * @return a view over a copy of them
	 */
	public static Uint8Array toUnsignedView(byte[] bytes) {
		return new Uint8Array(toBuffer(bytes));
	}

	/**
	 * A buffer as a Java array.
	 *
	 * @param buffer the buffer
	 * @return a copy of its bytes
	 */
	public static byte[] fromBuffer(ArrayBuffer buffer) {
		Objects.requireNonNull(buffer, "buffer");
		return new Int8Array(buffer).copyToJavaArray();
	}

	/**
	 * A view as a Java array.
	 *
	 * <p>Reads the region the view covers rather than the whole buffer behind it, so a view onto part
	 * of a larger buffer converts to that part.
	 *
	 * @param view the view
	 * @return a copy of the bytes it covers
	 */
	public static byte[] fromView(ArrayBufferView view) {
		Objects.requireNonNull(view, "view");
		return new Int8Array(
			view.getBuffer(),
			view.getByteOffset(),
			view.getByteLength()
		).copyToJavaArray();
	}
}
