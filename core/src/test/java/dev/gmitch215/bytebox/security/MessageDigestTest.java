package dev.gmitch215.bytebox.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("message digests")
class MessageDigestTest {

	private static final String[] ALGORITHMS = { "MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512" };
	private static final int[] LENGTHS = {
		0,
		1,
		55,
		56,
		63,
		64,
		65,
		111,
		112,
		113,
		127,
		128,
		129,
		1000,
		4096
	};
	private static final String TWO_BLOCK_64 =
		"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
	private static final String TWO_BLOCK_128 =
		"abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu";

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder();
		for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
		return out.toString();
	}

	private static String digest(String algorithm, byte[] input) throws NoSuchAlgorithmException {
		return hex(MessageDigest.getInstance(algorithm).digest(input));
	}

	private static String digest(String algorithm, String input) throws NoSuchAlgorithmException {
		return digest(algorithm, input.getBytes(StandardCharsets.US_ASCII));
	}

	private static byte[] aMillion() {
		byte[] bytes = new byte[1_000_000];
		Arrays.fill(bytes, (byte) 'a');
		return bytes;
	}

	@Test
	@DisplayName("matches the published vectors for the empty message and abc")
	void vectors() throws Exception {
		assertEquals("d41d8cd98f00b204e9800998ecf8427e", digest("MD5", ""));
		assertEquals("900150983cd24fb0d6963f7d28e17f72", digest("MD5", "abc"));
		assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", digest("SHA-1", ""));
		assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", digest("SHA-1", "abc"));
		assertEquals(
			"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
			digest("SHA-256", "")
		);
		assertEquals(
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			digest("SHA-256", "abc")
		);
		assertEquals(
			"38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
			digest("SHA-384", "")
		);
		assertEquals(
			"cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7",
			digest("SHA-384", "abc")
		);
		assertEquals(
			"cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
			digest("SHA-512", "")
		);
		assertEquals(
			"ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
			digest("SHA-512", "abc")
		);
	}

	@Test
	@DisplayName("matches the published vectors for messages that cross a block")
	void twoBlocks() throws Exception {
		assertEquals("57edf4a22be3c955ac49da2e2107b67a", digest("MD5", "1234567890".repeat(8)));
		assertEquals("84983e441c3bd26ebaae4aa1f95129e5e54670f1", digest("SHA-1", TWO_BLOCK_64));
		assertEquals(
			"248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
			digest("SHA-256", TWO_BLOCK_64)
		);
		assertEquals(
			"09330c33f71147e83d192fc782cd1b4753111b173b3b05d22fa08086e3b0f712fcc7c71a557e2db966c3e9fa91746039",
			digest("SHA-384", TWO_BLOCK_128)
		);
		assertEquals(
			"8e959b75dae313da8cf4f72814fc143f8f7779c6eb9f7fa17299aeadb6889018501d289e4900f7e4331b99dec4b5433ac7d329eeb6dd26545e96e55b874be909",
			digest("SHA-512", TWO_BLOCK_128)
		);
	}

	@Test
	@DisplayName("matches the published vectors for a million a")
	void millionA() throws Exception {
		byte[] input = aMillion();

		assertEquals("7707d6ae4e027c70eea2a935c2296f21", digest("MD5", input));
		assertEquals("34aa973cd4c4daa4f61eeb2bdbad27316534016f", digest("SHA-1", input));
		assertEquals(
			"cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
			digest("SHA-256", input)
		);
		assertEquals(
			"9d0e1809716474cb086e834e310a4a1ced149e9c00f248527972cec5704c2a5b07b8b3dc38ecc4ebae97ddd87f3d8985",
			digest("SHA-384", input)
		);
		assertEquals(
			"e718483d0ce769644e2e42c7bc15b4638e1f98b13b2044285632a803afa973ebde0ff244877ea60a4cb0432ce577c31beb009c5c2c49aa2e4eadb217ad8cc09b",
			digest("SHA-512", input)
		);
	}

	@Test
	@DisplayName("agrees with the JVM at every block boundary, in one call and in split calls")
	void agreesWithTheJvm() throws Exception {
		Random random = new Random(103);
		for (String algorithm : ALGORITHMS) {
			for (int length : LENGTHS) {
				byte[] input = new byte[length];
				random.nextBytes(input);
				byte[] expected = java.security.MessageDigest.getInstance(algorithm).digest(input);

				MessageDigest whole = MessageDigest.getInstance(algorithm);
				assertArrayEquals(expected, whole.digest(input), algorithm + " " + length);

				MessageDigest split = MessageDigest.getInstance(algorithm);
				int cut = length / 3;
				split.update(input, 0, cut);
				if (length > cut) split.update(input[cut]);
				split.update(input, Math.min(cut + 1, length), Math.max(0, length - cut - 1));
				assertArrayEquals(expected, split.digest(), algorithm + " split " + length);

				MessageDigest bytewise = MessageDigest.getInstance(algorithm);
				for (byte b : input) bytewise.update(b);
				assertArrayEquals(expected, bytewise.digest(), algorithm + " bytewise " + length);
			}
		}
	}

	@Test
	@DisplayName("starts over after a digest, a reset, and a digest of the input given to it")
	void reuse() throws Exception {
		for (String algorithm : ALGORITHMS) {
			MessageDigest digest = MessageDigest.getInstance(algorithm);
			byte[] first = digest.digest("abc".getBytes(StandardCharsets.US_ASCII));
			assertArrayEquals(
				first,
				digest.digest("abc".getBytes(StandardCharsets.US_ASCII)),
				algorithm
			);

			digest.update(new byte[200]);
			digest.reset();
			assertArrayEquals(
				first,
				digest.digest("abc".getBytes(StandardCharsets.US_ASCII)),
				algorithm
			);

			digest.update("a".getBytes(StandardCharsets.US_ASCII));
			assertArrayEquals(
				first,
				digest.digest("bc".getBytes(StandardCharsets.US_ASCII)),
				algorithm
			);
		}
	}

	@Test
	@DisplayName("reads a byte buffer from its position and leaves it drained")
	void buffers() throws Exception {
		byte[] input = "hello world".getBytes(StandardCharsets.US_ASCII);
		ByteBuffer buffer = ByteBuffer.wrap(input);
		buffer.position(6);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(buffer);

		assertEquals(0, buffer.remaining());
		assertEquals(digest("SHA-256", "world"), hex(digest.digest()));
	}

	@Test
	@DisplayName("writes a digest into the caller's array at an offset")
	void intoAnArray() throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		digest.update("abc".getBytes(StandardCharsets.US_ASCII));
		byte[] out = new byte[25];

		assertEquals(20, digest.digest(out, 3, 20));
		byte[] expected = java.security.MessageDigest.getInstance("SHA-1").digest(
			"abc".getBytes(StandardCharsets.US_ASCII)
		);
		assertArrayEquals(expected, Arrays.copyOfRange(out, 3, 23));
		assertEquals(0, out[0]);
		assertEquals(0, out[24]);

		assertThrows(DigestException.class, () -> digest.digest(new byte[40], 0, 10));
		assertThrows(IllegalArgumentException.class, () -> digest.digest(new byte[40], 30, 20));
		assertThrows(IllegalArgumentException.class, () -> digest.digest(null, 0, 20));
	}

	@Test
	@DisplayName("resolves the names the JDK does, whatever the case, and names none it lacks")
	void names() throws Exception {
		String[][] known = {
			{ "MD5", "MD5" },
			{ "md5", "MD5" },
			{ "SHA", "SHA-1" },
			{ "SHA1", "SHA-1" },
			{ "sha-1", "SHA-1" },
			{ "SHA256", "SHA-256" },
			{ "sha-256", "SHA-256" },
			{ "SHA384", "SHA-384" },
			{ "SHA-384", "SHA-384" },
			{ "SHA512", "SHA-512" },
			{ "sha-512", "SHA-512" },
			{ "1.2.840.113549.2.5", "MD5" },
			{ "1.3.14.3.2.26", "SHA-1" },
			{ "OID.1.3.14.3.2.26", "SHA-1" },
			{ "2.16.840.1.101.3.4.2.1", "SHA-256" },
			{ "2.16.840.1.101.3.4.2.2", "SHA-384" },
			{ "2.16.840.1.101.3.4.2.3", "SHA-512" }
		};
		byte[] input = "names".getBytes(StandardCharsets.US_ASCII);
		for (String[] row : known) {
			MessageDigest ours = MessageDigest.getInstance(row[0]);
			java.security.MessageDigest jdk = java.security.MessageDigest.getInstance(row[0]);
			assertEquals(row[0], ours.getAlgorithm());
			assertEquals(jdk.getAlgorithm(), ours.getAlgorithm());
			assertEquals(jdk.getDigestLength(), ours.getDigestLength(), row[0]);
			assertArrayEquals(jdk.digest(input), ours.digest(input), row[0]);
			assertEquals(row[1], digestNameOf(row[0]));
		}

		for (String missing : new String[] {
			"SHA-224",
			"SHA-512/256",
			"SHA3-256",
			"MD2",
			" MD5",
			""
		}) {
			NoSuchAlgorithmException thrown = assertThrows(
				NoSuchAlgorithmException.class,
				() -> MessageDigest.getInstance(missing),
				missing
			);
			assertEquals(missing + " MessageDigest not available", thrown.getMessage());
		}
		assertThrows(NullPointerException.class, () -> MessageDigest.getInstance(null));
	}

	private static String digestNameOf(String alias) throws Exception {
		return switch (MessageDigest.getInstance(alias).getDigestLength()) {
			case 16 -> "MD5";
			case 20 -> "SHA-1";
			case 32 -> "SHA-256";
			case 48 -> "SHA-384";
			default -> "SHA-512";
		};
	}

	@Test
	@DisplayName("reports the digest length and compares in constant shape like the JDK")
	void small() throws Exception {
		assertEquals(16, MessageDigest.getInstance("MD5").getDigestLength());
		assertEquals(20, MessageDigest.getInstance("SHA-1").getDigestLength());
		assertEquals(32, MessageDigest.getInstance("SHA-256").getDigestLength());
		assertEquals(48, MessageDigest.getInstance("SHA-384").getDigestLength());
		assertEquals(64, MessageDigest.getInstance("SHA-512").getDigestLength());

		assertTrue(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 2 }));
		assertFalse(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 3 }));
		assertFalse(MessageDigest.isEqual(new byte[] { 1 }, new byte[] { 1, 2 }));
		assertTrue(MessageDigest.isEqual(null, null));
		assertFalse(MessageDigest.isEqual(new byte[1], null));
	}

	@Test
	@DisplayName("says what it is and whether input is pending")
	void text() throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		assertTrue(digest.toString().startsWith("SHA-1 Message Digest"));
		assertTrue(digest.toString().contains("<initialized>"));
		digest.update((byte) 1);
		assertTrue(digest.toString().contains("<in progress>"));
	}

	@Test
	@DisplayName("refuses bad input ranges the way the JDK does")
	void ranges() throws Exception {
		MessageDigest digest = MessageDigest.getInstance("MD5");
		assertThrows(IllegalArgumentException.class, () -> digest.update(null, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> digest.update(new byte[2], 1, 5));
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> digest.update(new byte[2], -1, 1));
		assertThrows(NullPointerException.class, () -> digest.update((byte[]) null));
	}
}
