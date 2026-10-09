package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.js.TSObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The route that hashes fixed input with each algorithm, kept apart from the rest of the fixture. */
final class DigestRoutes {

	private static final String[] ALGORITHMS = { "MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512" };

	private DigestRoutes() {}

	static Response route() throws Exception {
		TSObject answer = TSObject.object();
		byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
		for (String algorithm : ALGORITHMS) {
			answer.set(algorithm, TSObject.of(hex(MessageDigest.getInstance(algorithm).digest(abc))));
		}

		byte[] long130 = new byte[130];
		for (int i = 0; i < long130.length; i++) long130[i] = (byte) i;
		for (String algorithm : ALGORITHMS) {
			MessageDigest split = MessageDigest.getInstance(algorithm);
			split.update(long130, 0, 70);
			split.update(long130, 70, 60);
			answer.set("split " + algorithm, TSObject.of(hex(split.digest())));
		}

		try {
			MessageDigest.getInstance("SHA-224");
		} catch (NoSuchAlgorithmException missing) {
			answer.set("missing", TSObject.of(missing.getMessage()));
		}
		return Bytebox.json(answer);
	}

	private static String hex(byte[] bytes) {
		char[] digits = "0123456789abcdef".toCharArray();
		StringBuilder out = new StringBuilder();
		for (byte b : bytes) out.append(digits[(b >> 4) & 15]).append(digits[b & 15]);
		return out.toString();
	}
}
