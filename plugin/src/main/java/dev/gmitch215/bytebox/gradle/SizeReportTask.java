package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Measures the compiled module and prints what the platform meters beside what it does not.
 *
 * <p>Cloudflare meters the uncompressed bundle, so the raw figure is the one that binds. Wrangler
 * still prints a gzip figure and this report keeps it for comparison, but no ceiling is enforced
 * against it. The number worth watching is growth: a Java Worker sits orders of magnitude under
 * 64 MiB, and what a larger module costs is startup.
 *
 * @since 1.0.0
 */
@DisableCachingByDefault(
	because = "the report is written to the build log, so a cache hit would print nothing"
)
public abstract class SizeReportTask extends DefaultTask {

	/** {@return the compiled module to measure} */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getWasm();

	/** {@return the ceiling to compare against, or -1 for none} */
	@Internal
	public abstract Property<Long> getBudget();

	/** Measures the module and writes the result to the build log. */
	@TaskAction
	public void report() {
		Path wasm = getWasm().get().getAsFile().toPath();
		byte[] raw;
		try {
			raw = Files.readAllBytes(wasm);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (String row : rows(wasm.getFileName().toString(), raw, getBudget().getOrElse(-1L))) {
			getLogger().lifecycle("{}", row);
		}
	}

	/**
	 * The report itself, apart from the log it is written to.
	 *
	 * @param name the module's file name
	 * @param raw the module
	 * @param budget the ceiling, or -1 for none
	 * @return one line per row
	 */
	static List<String> rows(String name, byte[] raw, long budget) {
		List<String> rows = new ArrayList<>();
		rows.add(name);
		rows.add("  raw              " + raw.length + "   <- the meter Cloudflare enforces");
		rows.add("  gzip -6          " + Compression.gzip(raw).length + "   reference only");
		rows.add("  gzip -9          " + Compression.gzip(raw, 9).length + "   reference only");
		rows.add(
			"  carried as       " +
				(raw.length < Compression.BUNDLE_CEILING
					? "raw bytes, with nothing to inflate at startup"
					: "a compressed frame, the only shape the platform accepts this large")
		);
		if (budget > 0) {
			long headroom = budget - raw.length;
			rows.add(
				"  budget           " +
					budget +
					" (" +
					Math.abs(headroom) +
					" " +
					(headroom >= 0 ? "to spare" : "over") +
					")"
			);
		}
		rows.add("  either plan      " + Compression.BUNDLE_CEILING);
		rows.add("  startup          1000 ms, which is what a larger module spends");
		return rows;
	}
}
