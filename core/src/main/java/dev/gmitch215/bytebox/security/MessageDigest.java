package dev.gmitch215.bytebox.security;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

/**
 * {@code java.security.MessageDigest}, which the class library does not carry, for MD5, SHA-1,
 * SHA-256, SHA-384 and SHA-512.
 *
 * <p>Written in Java rather than backed by {@code SubtleCrypto.digest}, because that returns a
 * promise and a substituted class cannot suspend. SHA-224, SHA-512/256, SHA-3 and the MD2 digest are
 * not provided; asking for them throws {@link NoSuchAlgorithmException} the way an unknown name does
 * on a JVM. Providers are not modelled: there is no {@code getInstance(String, String)} and no
 * {@code getProvider}, and no {@code clone}.
 *
 * @since 1.0.3
 */
public final class MessageDigest {

	private final String algorithm;
	private final Engine engine;

	private MessageDigest(String algorithm, Engine engine) {
		this.algorithm = algorithm;
		this.engine = engine;
	}

	/**
	 * @param algorithm a name such as {@code SHA-256}, in any case, or one of the JDK's aliases
	 * @return a digest that has taken no input
	 * @throws NoSuchAlgorithmException when the algorithm is not one of the five
	 */
	public static MessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
		if (algorithm == null) throw new NullPointerException("null algorithm name");
		Engine engine = Engine.named(algorithm);
		if (engine == null) {
			throw new NoSuchAlgorithmException(algorithm + " MessageDigest not available");
		}
		return new MessageDigest(algorithm, engine);
	}

	/**
	 * @param input the byte to add
	 */
	public void update(byte input) {
		engine.update(new byte[] { input }, 0, 1);
	}

	/**
	 * @param input the bytes to add
	 */
	public void update(byte[] input) {
		engine.update(input, 0, input.length);
	}

	/**
	 * @param input the array to read from
	 * @param offset where in it to start
	 * @param len how many bytes to add
	 */
	public void update(byte[] input, int offset, int len) {
		if (input == null) throw new IllegalArgumentException("No input buffer given");
		if (offset < 0 || len < 0) {
			throw new ArrayIndexOutOfBoundsException("offset " + offset + ", length " + len);
		}
		if (input.length - offset < len) throw new IllegalArgumentException(
			"Input buffer too short"
		);
		engine.update(input, offset, len);
	}

	/**
	 * @param input the buffer to drain from its position to its limit
	 */
	public void update(ByteBuffer input) {
		byte[] rest = new byte[input.remaining()];
		input.get(rest);
		engine.update(rest, 0, rest.length);
	}

	/**
	 * @return the digest of everything added, after which this is as new
	 */
	public byte[] digest() {
		return engine.finish();
	}

	/**
	 * @param input the last bytes to add
	 * @return the digest of everything added, after which this is as new
	 */
	public byte[] digest(byte[] input) {
		update(input);
		return digest();
	}

	/**
	 * @param buf where to put the digest
	 * @param offset where in it to start
	 * @param len how much room to use, at least the digest length
	 * @return the number of bytes written
	 * @throws DigestException when {@code len} is shorter than the digest
	 */
	public int digest(byte[] buf, int offset, int len) throws DigestException {
		if (buf == null) throw new IllegalArgumentException("No output buffer given");
		if (len < engine.digestLength()) {
			throw new DigestException(
				"Length must be at least " +
					engine.digestLength() +
					" for " +
					algorithm +
					" digests"
			);
		}
		if (offset < 0 || buf.length - offset < len) {
			throw new IllegalArgumentException(
				"Output buffer too small for specified offset and length"
			);
		}
		byte[] digest = engine.finish();
		System.arraycopy(digest, 0, buf, offset, digest.length);
		return digest.length;
	}

	/** Drops everything added so far. */
	public void reset() {
		engine.reset();
	}

	/**
	 * @return the name this was asked for, as given
	 */
	public String getAlgorithm() {
		return algorithm;
	}

	/**
	 * @return the digest length in bytes
	 */
	public int getDigestLength() {
		return engine.digestLength();
	}

	/**
	 * @param a one digest, or null
	 * @param b another, or null
	 * @return whether they are equal, without stopping at the first difference
	 */
	public static boolean isEqual(byte[] a, byte[] b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (a.length != b.length) return false;
		int difference = 0;
		for (int i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
		return difference == 0;
	}

	@Override
	public String toString() {
		return (
			algorithm +
			" Message Digest from bytebox, " +
			(engine.pristine() ? "<initialized>" : "<in progress>") +
			"\n"
		);
	}
}
