package dev.gmitch215.bytebox.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/** Compresses a module the way the size meter does. */
final class Compression {

	/**
	 * Where a pre-compressed frame starts paying for itself, in bytes of raw WebAssembly.
	 *
	 * <p>Below this, raw bytes measure smaller because Cloudflare's own gzip is already applied and a
	 * synchronous decompressor costs about 5.6 KB of bundle flat. Above it, the frame saves more than
	 * the decompressor costs. Measured between 56 KB and 216 KB of raw wasm on real binaries.
	 */
	static final long COMPRESSION_CROSSOVER = 130_000;

	private Compression() {}

	/**
	 * Compresses at the level Cloudflare's meter applies.
	 *
	 * @param data the bytes to compress
	 * @return the compressed bytes
	 */
	static byte[] gzip(byte[] data) {
		return gzip(data, Deflater.DEFAULT_COMPRESSION);
	}

	/**
	 * Compresses at an explicit level.
	 *
	 * @param data the bytes to compress
	 * @param level the deflate level
	 * @return the compressed bytes
	 */
	static byte[] gzip(byte[] data, int level) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (
			GZIPOutputStream gz = new GZIPOutputStream(out) {
				{
					def.setLevel(level);
				}
			}
		) {
			gz.write(data);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}
}
