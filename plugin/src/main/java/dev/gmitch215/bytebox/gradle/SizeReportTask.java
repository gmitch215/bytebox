package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

		int gzipped = Compression.gzip(raw).length;
		getLogger().lifecycle("{}", wasm.getFileName());
		getLogger().lifecycle("  raw              {}", raw.length);
		getLogger().lifecycle("  gzip -6          {}   <- the meter Cloudflare enforces", gzipped);
		getLogger().lifecycle("  gzip -9          {}", Compression.gzip(raw, 9).length);
		getLogger().lifecycle(
			"  carried as       {}",
			raw.length < Compression.COMPRESSION_CROSSOVER
				? "raw bytes, which measure smaller below " +
						Compression.COMPRESSION_CROSSOVER +
						" raw"
				: "a compressed frame, which now saves more than its decoder costs"
		);

		long budget = getBudget().getOrElse(-1L);
		if (budget > 0) {
			long headroom = budget - gzipped;
			getLogger().lifecycle(
				"  budget           {} ({} {})",
				budget,
				Math.abs(headroom),
				headroom >= 0 ? "to spare" : "over"
			);
		}
		getLogger().lifecycle("  free plan        3145728");
		getLogger().lifecycle("  paid plan        10485760");
	}
}
