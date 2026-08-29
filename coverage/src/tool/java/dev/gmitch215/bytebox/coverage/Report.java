package dev.gmitch215.bytebox.coverage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.xml.XMLFormatter;

/**
 * Turns the probes an instrumented module recorded into the report format the JVM lane already
 * uploads.
 *
 * <p>Run as {@code Report <dump> <classes> <sources> <output.xml>}. The classes analysed are the
 * uninstrumented ones, which is what makes the line numbers the ones a reader sees; JaCoCo pairs a
 * probe array with a class by the identifier it computed from those same bytes.
 */
public final class Report {

	private Report() {}

	/**
	 * @param args the dump, the uninstrumented classes, the sources, and where the report goes
	 * @throws IOException when anything cannot be read or written
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			throw new IllegalArgumentException(
				"usage: Report <dump> <classes> <sources> <output.xml>"
			);
		}
		Path dump = Path.of(args[0]);
		Path classes = Path.of(args[1]);
		Path sources = Path.of(args[2]);
		Path output = Path.of(args[3]);

		ExecutionDataStore executions = read(dump);
		CoverageBuilder builder = new CoverageBuilder();
		analyze(new Analyzer(executions, builder), classes);
		IBundleCoverage bundle = builder.getBundle("bytebox-core (workerd)");

		SessionInfoStore sessions = new SessionInfoStore();
		sessions.visitSessionInfo(
			new SessionInfo("workerd", System.currentTimeMillis(), System.currentTimeMillis())
		);

		Files.createDirectories(output.getParent());
		try (OutputStream out = Files.newOutputStream(output)) {
			IReportVisitor visitor = new XMLFormatter().createVisitor(out);
			visitor.visitInfo(sessions.getInfos(), executions.getContents());
			visitor.visitBundle(
				bundle,
				new DirectorySourceFileLocator(sources.toFile(), "UTF-8", 4)
			);
			visitor.visitEnd();
		}

		System.out.println(
			"bytebox: " +
				bundle.getLineCounter().getCoveredCount() +
				"/" +
				bundle.getLineCounter().getTotalCount() +
				" lines from the workerd lane, written to " +
				output
		);
	}

	/**
	 * Reads every class the same way the instrumenter did.
	 *
	 * <p>Walked here rather than through {@code analyzeAll}, because each class has to be lowered
	 * first: JaCoCo identifies a class by a checksum over its bytes, and a class hashed at one
	 * version does not match the same class hashed at another. Hashing the two differently is what
	 * produces a report where every probe was recorded and nothing is covered.
	 *
	 * @param analyzer the analyzer to feed
	 * @param classes the uninstrumented classes
	 * @throws IOException when a class cannot be read
	 */
	private static void analyze(Analyzer analyzer, Path classes) throws IOException {
		try (java.util.stream.Stream<Path> walk = Files.walk(classes)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				if (!file.toString().endsWith(".class")) continue;
				analyzer.analyzeClass(Versions.lower(Files.readAllBytes(file)), file.toString());
			}
		}
	}

	/** One class per line: the identifier, the class name, and a character per probe. */
	private static ExecutionDataStore read(Path dump) throws IOException {
		ExecutionDataStore store = new ExecutionDataStore();
		for (String line : Files.readAllLines(dump)) {
			if (line.isBlank()) continue;
			String[] parts = line.split(" ", 3);
			if (parts.length != 3) {
				throw new IllegalStateException("not a probe line: " + line);
			}
			String recorded = parts[2].trim();
			boolean[] probes = new boolean[recorded.length()];
			for (int i = 0; i < recorded.length(); i++) probes[i] = recorded.charAt(i) == '1';
			store.put(new ExecutionData(Long.parseLong(parts[0]), parts[1], probes));
		}
		return store;
	}
}
