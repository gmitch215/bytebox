package dev.gmitch215.bytebox.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code size { }} block: how the compiled module is packed, and how big it may get.
 *
 * @since 1.0.0
 */
public abstract class SizeSpec {

	/** {@return how the compiled module is carried in the bundle} */
	public abstract Property<ModuleType> getModule();

	/** {@return which compressor packs the module} */
	public abstract Property<Compressor> getCompression();

	/** {@return the compression level, passed through to the chosen compressor} */
	public abstract Property<Integer> getCompressionLevel();

	/** {@return extra arguments for the chosen compressor} */
	public abstract ListProperty<String> getCompressionArgs();

	/**
	 * {@return a ceiling the build fails past, measured on Wrangler's gzip meter}
	 *
	 * <p>Takes a plain byte count or a suffixed size: {@code 250KiB}, {@code 3MB}, {@code 1MiB}.
	 * Cloudflare enforces 3 MB on the free plan and 10 MB on paid, after its own gzip, so a budget
	 * is for noticing a regression long before the platform does.
	 */
	public abstract Property<String> getBudget();

	/**
	 * Adds compressor arguments.
	 *
	 * @param args the arguments
	 */
	public void compressionArgs(String... args) {
		List<String> all = new ArrayList<>(getCompressionArgs().get());
		all.addAll(List.of(args));
		getCompressionArgs().set(all);
	}

	/**
	 * The budget in bytes, or -1 when none is set.
	 *
	 * @return the ceiling
	 */
	public long budgetBytes() {
		if (!getBudget().isPresent()) return -1;
		return parseSize(getBudget().get());
	}

	/**
	 * Reads a size, with or without a unit.
	 *
	 * <p>Both conventions are accepted because both are in use: {@code KiB} is 1024 and {@code KB} is
	 * 1000, and Cloudflare's own documentation mixes them. Being explicit beats guessing.
	 *
	 * @param text the size
	 * @return the byte count
	 */
	static long parseSize(String text) {
		String trimmed = text.trim().replace("_", "");
		long multiplier = 1;
		String digits = trimmed;
		String[][] units = {
			{ "KiB", "1024" },
			{ "MiB", "1048576" },
			{ "GiB", "1073741824" },
			{ "KB", "1000" },
			{ "MB", "1000000" },
			{ "GB", "1000000000" },
			{ "K", "1024" },
			{ "M", "1048576" },
			{ "G", "1073741824" },
			{ "B", "1" }
		};
		for (String[] unit : units) {
			if (
				trimmed.regionMatches(
					true,
					trimmed.length() - unit[0].length(),
					unit[0],
					0,
					unit[0].length()
				)
			) {
				multiplier = Long.parseLong(unit[1]);
				digits = trimmed.substring(0, trimmed.length() - unit[0].length());
				break;
			}
		}
		try {
			return (long) (Double.parseDouble(digits.trim()) * multiplier);
		} catch (NumberFormatException notASize) {
			throw new IllegalArgumentException(
				"could not read " + text + " as a size; write a byte count or one like 250KiB",
				notASize
			);
		}
	}

	/** How the compiled module is carried in the bundle. */
	public enum ModuleType {
		/**
		 * Picks on measured size: raw bytes below the crossover, a compressed frame above it.
		 *
		 * <p>The crossover is where a frame starts saving more than the decompressor costs, which is
		 * near 120 KB of raw WebAssembly.
		 */
		AUTO,
		/**
		 * Raw bytes in a {@code Data} module, compressed by Cloudflare's own gzip.
		 *
		 * <p>Smallest below the crossover, and needs no decompressor in the bundle at all.
		 */
		DATA,
		/**
		 * A compressed frame in a {@code Data} module, inflated at module scope.
		 *
		 * <p>Wins once the frame saves more than the decompressor costs. The decompressor has to be
		 * synchronous, because a module-scope await never settles on this runtime.
		 */
		DATA_COMPRESSED
	}

	/**
	 * Which compressor packs the module.
	 *
	 * <p>The figure that matters is the frame plus the decompressor, not the frame alone. Brotli
	 * compresses better than zstd and its synchronous decoder carries a 122 KB static dictionary,
	 * which eats most of the win at these sizes.
	 */
	public enum Compressor {
		/** No pre-compression; Cloudflare's gzip does the work. */
		NONE(null, null),
		/** zstd, inflated by {@code fzstd}. */
		ZSTD("fzstd", "^0.1.1"),
		/** gzip, inflated by {@code fflate}. */
		GZIP("fflate", "^0.8.3");

		private final String decoder;
		private final String version;

		Compressor(String decoder, String version) {
			this.decoder = decoder;
			this.version = version;
		}

		/** {@return the npm package holding the synchronous decoder, or null when none is needed} */
		public String decoder() {
			return decoder;
		}

		/** {@return the version range for that package} */
		public String version() {
			return version;
		}
	}
}
