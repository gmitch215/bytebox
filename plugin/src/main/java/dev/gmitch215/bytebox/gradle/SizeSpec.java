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

	/**
	 * {@return which compressor packs the module}
	 *
	 * @deprecated nothing reads this. Cloudflare removed the compressed size limit on 2026-09-04, so
	 *     pre-compression trades startup time for bundle bytes that are no longer metered.
	 */
	@Deprecated(since = "1.0.1", forRemoval = true)
	public abstract Property<Compressor> getCompression();

	/**
	 * {@return the compression level, passed through to the chosen compressor}
	 *
	 * @deprecated see {@link #getCompression()}
	 */
	@Deprecated(since = "1.0.1", forRemoval = true)
	public abstract Property<Integer> getCompressionLevel();

	/**
	 * {@return extra arguments for the chosen compressor}
	 *
	 * @deprecated see {@link #getCompression()}
	 */
	@Deprecated(since = "1.0.1", forRemoval = true)
	public abstract ListProperty<String> getCompressionArgs();

	/**
	 * {@return a ceiling the build fails past, measured on the uncompressed module}
	 *
	 * <p>Takes a plain byte count or a suffixed size: {@code 250KiB}, {@code 3MB}, {@code 1MiB}.
	 * Cloudflare accepts 64 MiB uncompressed on either plan and meters nothing else, so a budget is
	 * not there to keep a Java Worker deployable. It is there to fail the build when the compiled
	 * module grows, because compiling it is what spends the one second a Worker has to reach its
	 * first request. This measures the module, which is code; bundle bytes that are not code cost
	 * startup nothing.
	 */
	public abstract Property<String> getBudget();

	/**
	 * Adds compressor arguments.
	 *
	 * @param args the arguments
	 * @deprecated see {@link #getCompression()}
	 */
	@Deprecated(since = "1.0.1", forRemoval = true)
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
		 * Raw bytes, unless the module is too large for the platform to accept them.
		 *
		 * <p>A Java module reaches the second case only past 64 MiB, which is where compression
		 * stops being a trade and becomes the only way to deploy at all.
		 */
		AUTO,
		/**
		 * Raw bytes in a {@code Data} module.
		 *
		 * <p>Nothing to inflate at module scope, so the startup budget pays for compiling the module
		 * and nothing else.
		 */
		DATA,

		/**
		 * A compressed frame in a {@code Data} module, inflated at module scope.
		 *
		 * <p>The decompressor has to be synchronous, because a module-scope await never settles on
		 * this runtime, and inflating runs inside the one second a Worker has to start.
		 *
		 * @deprecated buys bundle bytes Cloudflare stopped metering on 2026-09-04, and spends startup
		 *     time it still meters
		 */
		@Deprecated(since = "1.0.1", forRemoval = true)
		DATA_COMPRESSED
	}

	/**
	 * Which compressor packs the module.
	 *
	 * @deprecated see {@link SizeSpec#getCompression()}
	 */
	@Deprecated(since = "1.0.1", forRemoval = true)
	public enum Compressor {
		/** No pre-compression, and nothing to inflate at startup. */
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
