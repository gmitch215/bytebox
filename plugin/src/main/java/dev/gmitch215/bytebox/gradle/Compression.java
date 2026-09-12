package dev.gmitch215.bytebox.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/** Compresses a module, for the reference figure beside the one the platform meters. */
final class Compression {

	/**
	 * The largest Worker Cloudflare accepts, uncompressed, on either plan.
	 *
	 * <p>Since 2026-09-04 there is no compressed size limit and only the uncompressed bundle counts,
	 * so this is the only ceiling a build can hit. A Java Worker does not approach it. What costs a
	 * Worker is compiled code, metered separately as startup; bundle bytes that are not code measured
	 * free.
	 */
	static final long BUNDLE_CEILING = 67_108_864L;

	private Compression() {}

	/**
	 * Compresses at the default level, for the figure wrangler prints for reference.
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
