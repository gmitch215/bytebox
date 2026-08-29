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
 * Measures the compiled module and prints one row per compression axis.
 *
 * <p>Cloudflare enforces its ceiling after its own gzip, so the gzip figure is the one that binds and
 * a local {@code gzip -c | wc -c} is not that meter. A pre-compressed frame only pays once it saves
 * more than a synchronous decompressor costs in the same bundle, which is why the crossover is
 * reported rather than assumed.
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
		int gzipped = Compression.gzip(raw).length;
		List<String> rows = new ArrayList<>();
		rows.add(name);
		rows.add("  raw              " + raw.length);
		rows.add("  gzip -6          " + gzipped + "   <- the meter Cloudflare enforces");
		rows.add("  gzip -9          " + Compression.gzip(raw, 9).length);
		rows.add(
			"  carried as       " +
				(raw.length < Compression.COMPRESSION_CROSSOVER
					? "raw bytes, which measure smaller below " +
						Compression.COMPRESSION_CROSSOVER +
						" raw"
					: "a compressed frame, which now saves more than its decoder costs")
		);
		if (budget > 0) {
			long headroom = budget - gzipped;
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
		rows.add("  free plan        3145728");
		rows.add("  paid plan        10485760");
		return rows;
	}
}
