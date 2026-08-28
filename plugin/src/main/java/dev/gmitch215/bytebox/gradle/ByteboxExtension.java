package dev.gmitch215.bytebox.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code bytebox { }} block.
 *
 * @since 1.0.0
 */
public abstract class ByteboxExtension {

	/** {@return the class implementing one or more of the trigger interfaces} */
	public abstract Property<String> getHandlerClass();

	/** {@return the name written into the generated Wrangler configuration} */
	public abstract Property<String> getWorkerName();

	/**
	 * {@return the compatibility date written into the generated Wrangler configuration}
	 *
	 * <p>Defaults to the build date. Dates from 2026-08-04 enable Node compatibility, which defines
	 * a {@code process} global; the generated scaffold disables it because TeaVM's runtime treats
	 * that global as proof it is running under Node.
	 */
	public abstract Property<String> getCompatibilityDate();

	/** {@return how the compiled module is carried in the bundle} */
	public abstract Property<ModuleType> getModuleType();

	/** {@return a size ceiling the build fails past, in bytes on wrangler's gzip meter} */
	public abstract Property<Long> getSizeBudget();

	/** {@return binding declarations, in the order they were added} */
	public abstract ListProperty<String> getBindings();

	/** How the compiled module is carried in the bundle. */
	public enum ModuleType {
		/**
		 * Picks on measured size. Raw bytes below the crossover, a compressed frame above it.
		 */
		AUTO,
		/**
		 * Raw bytes in a {@code Data} module, compressed by Cloudflare's own gzip. Smallest below
		 * roughly 120 KB of wasm, and needs no decompressor in the bundle.
		 */
		DATA,
		/**
		 * A zstd frame in a {@code Data} module, inflated at module scope. Wins once the frame saves
		 * more than the decompressor costs.
		 */
		DATA_ZSTD
	}
}
