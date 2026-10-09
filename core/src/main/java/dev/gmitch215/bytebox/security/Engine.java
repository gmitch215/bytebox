package dev.gmitch215.bytebox.security;

import java.util.Locale;

/**
 * The part every Merkle-Damgard hash shares: buffering input into blocks and padding the tail.
 *
 * <p>Not a substitute and not named by {@link Substitutions}; {@link MessageDigest} holds one by
 * composition, so nothing extends the substituted class.
 *
 * @since 1.0.3
 */
abstract class Engine {

	private final int blockSize;
	private final int lengthBytes;
	private final byte[] block;
	private int filled;
	private long total;

	Engine(int blockSize, int lengthBytes) {
		this.blockSize = blockSize;
		this.lengthBytes = lengthBytes;
		this.block = new byte[blockSize];
	}

	/** Resolves a JDK algorithm name or object identifier, or answers null. */
	static Engine named(String name) {
		String key = name.toUpperCase(Locale.ROOT);
		if (key.startsWith("OID.")) key = key.substring(4);
		switch (key) {
			case "MD5":
			case "1.2.840.113549.2.5":
				return new Md5();
			case "SHA":
			case "SHA1":
			case "SHA-1":
			case "1.3.14.3.2.26":
				return new Sha1();
			case "SHA256":
			case "SHA-256":
			case "2.16.840.1.101.3.4.2.1":
				return new Sha256();
			case "SHA384":
			case "SHA-384":
			case "2.16.840.1.101.3.4.2.2":
				return new Sha512(true);
			case "SHA512":
			case "SHA-512":
			case "2.16.840.1.101.3.4.2.3":
				return new Sha512(false);
			default:
				return null;
		}
	}

	abstract int digestLength();

	abstract void init();

	abstract void compress(byte[] data, int offset);

	abstract byte[] output();

	abstract boolean littleEndian();

	final boolean pristine() {
		return total == 0;
	}

	final void update(byte[] input, int offset, int length) {
		total += length;
		absorb(input, offset, length);
	}

	private void absorb(byte[] input, int offset, int length) {
		if (filled > 0) {
			int take = Math.min(length, blockSize - filled);
			System.arraycopy(input, offset, block, filled, take);
			filled += take;
			offset += take;
			length -= take;
			if (filled < blockSize) return;
			compress(block, 0);
			filled = 0;
		}
		while (length >= blockSize) {
			compress(input, offset);
			offset += blockSize;
			length -= blockSize;
		}
		System.arraycopy(input, offset, block, 0, length);
		filled = length;
	}

	final byte[] finish() {
		int room = blockSize - lengthBytes;
		int zeros = filled < room ? room - filled : room + blockSize - filled;
		byte[] tail = new byte[zeros + lengthBytes];
		tail[0] = (byte) 0x80;
		long bits = total << 3;
		long high = total >>> 61;
		int end = tail.length;
		for (int i = 0; i < 8; i++) {
			int at = littleEndian() ? zeros + i : end - 1 - i;
			tail[at] = (byte) (bits >>> (8 * i));
		}
		for (int i = 0; i < lengthBytes - 8; i++) {
			tail[end - 9 - i] = (byte) (high >>> (8 * i));
		}
		absorb(tail, 0, tail.length);
		byte[] result = output();
		reset();
		return result;
	}

	final void reset() {
		filled = 0;
		total = 0;
		init();
	}
}
