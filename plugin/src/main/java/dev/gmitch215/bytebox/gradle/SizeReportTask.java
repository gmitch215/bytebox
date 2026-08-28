package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Measures the compiled module and prints one row per compression axis.
 *
 * <p>Cloudflare enforces its ceiling after its own gzip, so the gzip figure is the one that binds.
 * A pre-compressed frame only pays once it saves more than a synchronous decompressor costs in the
 * same bundle.
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

		getLogger().lifecycle("{}", wasm.getFileName());
		getLogger().lifecycle("  raw     {}", raw.length);
		getLogger().lifecycle("  gzip-6  {}", Compression.gzip(raw).length);
	}
}
