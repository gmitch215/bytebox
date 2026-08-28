package dev.gmitch215.bytebox.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/** Compresses a module the way the size meter does. */
final class Compression {

	private Compression() {}

	/**
	 * Compresses at level 6, which is what Cloudflare's meter applies.
	 *
	 * @param data the bytes to compress
	 * @return the compressed bytes
	 */
	static byte[] gzip(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (
			GZIPOutputStream gz = new GZIPOutputStream(out) {
				{
					def.setLevel(Deflater.DEFAULT_COMPRESSION);
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
