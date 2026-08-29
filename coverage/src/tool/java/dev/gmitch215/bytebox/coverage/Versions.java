package dev.gmitch215.bytebox.coverage;

/**
 * Reads a class as an older one, which is the whole reason this lane works.
 *
 * <p>From Java 11 up, JaCoCo reaches a class's probe array through a dynamic constant. The
 * compiler's bytecode parser has no case for one and fails with a bare
 * {@code IllegalArgumentException} naming neither the class nor the constant. The strategy is chosen
 * from the class file version and nothing else, so presenting an older version picks a synthetic
 * field instead, which is bytecode any version can hold.
 *
 * <p>Both sides of the lane go through here, and that is not tidiness. JaCoCo identifies a class by
 * a checksum over its bytes, so the instrumenter and the analyser have to hash the same bytes or
 * every probe array is recorded against a class the report never finds.
 */
final class Versions {

	/** Below the version at which the probe array moves into a dynamic constant. */
	private static final int JAVA_8 = 52;

	private Versions() {}

	/**
	 * @param classFile the class as compiled
	 * @return a copy reading as Java 8
	 */
	static byte[] lower(byte[] classFile) {
		byte[] lowered = classFile.clone();
		lowered[6] = (byte) (JAVA_8 >> 8);
		lowered[7] = (byte) JAVA_8;
		return lowered;
	}

	/**
	 * @param classFile the class file to read
	 * @return its major version
	 */
	static int major(byte[] classFile) {
		return ((classFile[6] & 0xFF) << 8) | (classFile[7] & 0xFF);
	}

	/**
	 * @param classFile the class file to rewrite in place
	 * @param major the major version to write
	 */
	static void major(byte[] classFile, int major) {
		classFile[6] = (byte) (major >> 8);
		classFile[7] = (byte) major;
	}
}
