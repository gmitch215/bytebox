package dev.gmitch215.bytebox.coverage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jacoco.core.instr.Instrumenter;

/**
 * Rewrites a directory of class files with JaCoCo's own offline instrumenter.
 *
 * <p>Run as {@code Instrument <classes> <output>}. The probes JaCoCo inserts are the same ones the
 * JVM lane records, so the two lanes produce comparable data and a report built from either reads
 * the same class files.
 */
public final class Instrument {

	private Instrument() {}

	/**
	 * @param args the directory to read and the directory to write
	 * @throws IOException when a class cannot be read or written
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			throw new IllegalArgumentException("usage: Instrument <classes> <output>");
		}
		Path from = Path.of(args[0]);
		Path to = Path.of(args[1]);

		Instrumenter instrumenter = new Instrumenter(new Probing());
		List<Path> classes = new ArrayList<>();
		int left = 0;
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				String relative = from.relativize(file).toString().replace(File.separatorChar, '/');
				Path target = to.resolve(from.relativize(file).toString());
				Files.createDirectories(target.getParent());
				if (!relative.endsWith(".class") || compilerFacing(relative)) {
					Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					if (relative.endsWith(".class")) left++;
					continue;
				}
				byte[] original = Files.readAllBytes(file);
				Files.write(target, instrument(instrumenter, original, file.toString()));
				classes.add(file);
			}
		}
		System.out.println(
			"bytebox: instrumented " +
				classes.size() +
				" class(es) into " +
				to +
				", leaving " +
				left +
				" the compiler rewrites"
		);
	}

	/**
	 * Classes left exactly as they were built, for one of two reasons.
	 *
	 * <p>A substitution policy is loaded by the compiler's own service loader and executes inside it.
	 * Probing one records the compiler's execution rather than the Worker's, against a class the
	 * Worker never runs.
	 *
	 * <p>The retargeted packages are not on this list. The classes a policy names are rewritten before
	 * they are compiled, and probing them turns out to survive that: {@code java.net.Socket} carries
	 * its probes through the rename and reports them the same way anything else does.
	 */
	private static boolean compilerFacing(String relative) {
		return relative.endsWith("/Substitutions.class");
	}

	/**
	 * Instruments one class, reading it as an older one so the probes land in a field.
	 *
	 * <p>The version goes back afterwards, so what the compiler sees is a class at the version it was
	 * built at. See {@link Versions} for why the lowering happens at all, and why the report has to
	 * do the same thing.
	 *
	 * @param instrumenter the instrumenter
	 * @param original the class as compiled
	 * @param name the class's name, for the failure message
	 * @return the instrumented class, at its original version
	 * @throws IOException when the class cannot be read
	 */
	private static byte[] instrument(Instrumenter instrumenter, byte[] original, String name)
		throws IOException {
		byte[] instrumented = instrumenter.instrument(Versions.lower(original), name);
		Versions.major(instrumented, Versions.major(original));
		return instrumented;
	}
}
