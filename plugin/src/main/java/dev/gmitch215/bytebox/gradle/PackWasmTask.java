package dev.gmitch215.bytebox.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Puts the compiled module where the Worker imports it from, and enforces the size budget.
 *
 * <p>The module is carried as raw bytes rather than compiled by Wrangler, because a
 * {@code CompiledWasm} module is compiled by the platform and there is no way to pass it the JS
 * String Builtins option the compiler's output needs. Raw bytes also measure smaller at these sizes:
 * Cloudflare's own gzip is applied either way, and a pre-compressed frame has to pay for a
 * synchronous decompressor in the same bundle.
 *
 * @since 1.0.0
 */
@CacheableTask
public abstract class PackWasmTask extends DefaultTask {

	/** {@return the compiled module} */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getWasm();

	/** {@return the generated runtime beside it} */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getRuntime();

	/** {@return how the module is carried} */
	@Input
	public abstract Property<SizeSpec.ModuleType> getModuleType();

	/** {@return the ceiling to fail past, in bytes on the gzip meter, or -1 for none} */
	@Input
	public abstract Property<Long> getBudget();

	/** {@return where the Worker's sources live} */
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	/** {@return the compressor, when one was chosen} */
	@Input
	@Optional
	public abstract Property<SizeSpec.Compressor> getCompression();

	/** Packs the module. */
	@TaskAction
	public void pack() {
		Path source = getWasm().get().getAsFile().toPath();
		Path target = getOutputDirectory().get().getAsFile().toPath().resolve("src");

		byte[] raw;
		try {
			Files.createDirectories(target);
			raw = Files.readAllBytes(source);
			// `.wasmbin` rather than `.wasm`, so a bundler's own WebAssembly handling does not claim
			// it and the Data module rule is what applies
			Files.write(target.resolve("app.wasmbin"), raw);
			Files.copy(
				getRuntime().get().getAsFile().toPath(),
				target.resolve("app.wasm-runtime.js"),
				StandardCopyOption.REPLACE_EXISTING
			);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		long metered = Compression.gzip(raw).length;
		SizeSpec.ModuleType chosen = resolve(raw.length);
		getLogger().lifecycle(
			"bytebox: {} raw, {} on the gzip meter, carried as {}",
			raw.length,
			metered,
			chosen
		);

		long budget = getBudget().get();
		if (budget > 0 && metered > budget) {
			throw new GradleException(
				"the Worker measures " +
					metered +
					" bytes on the gzip meter, past the budget of " +
					budget +
					". Raise bytebox.size.budget, or find the growth with the sizeReport task."
			);
		}
	}

	/**
	 * Decides how to carry the module when the choice was left open.
	 *
	 * @param rawSize the module's size
	 * @return the resolved type
	 */
	SizeSpec.ModuleType resolve(long rawSize) {
		SizeSpec.ModuleType declared = getModuleType().get();
		if (declared != SizeSpec.ModuleType.AUTO) return declared;
		return rawSize < Compression.COMPRESSION_CROSSOVER
			? SizeSpec.ModuleType.DATA
			: SizeSpec.ModuleType.DATA_COMPRESSED;
	}
}
