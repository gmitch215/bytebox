package dev.gmitch215.bytebox.size;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Splits a compiled module's code section between the compiler's own runtime, the class library and
 * the program being measured.
 *
 * <p>The split is what decides whether a size problem can be reached from the build at all. On a
 * hello world the runtime dominates because it is close to a fixed cost; on a program that does real
 * work the class library dominates and the runtime becomes a rounding error. Attribution needs the
 * name section, so measure a module built with obfuscation off.
 *
 * @since 1.0.0
 */
public final class ModuleSplit {

	/** Where a function's bytes were charged. */
	public enum Bucket {
		/** The compiler's own emitted runtime. */
		RUNTIME,
		/** The Java class library. */
		CLASS_LIBRARY,
		/** The program being measured. */
		USER_CODE,
		/** A function the name section did not name. */
		UNNAMED
	}

	private final Map<Bucket, Long> bytes;
	private final Map<Bucket, Integer> functions;
	private final Map<String, Long> largest;

	private ModuleSplit(
		Map<Bucket, Long> bytes,
		Map<Bucket, Integer> functions,
		Map<String, Long> largest
	) {
		this.bytes = bytes;
		this.functions = functions;
		this.largest = largest;
	}

	/** {@return bytes charged to each bucket} */
	public Map<Bucket, Long> bytes() {
		return Map.copyOf(bytes);
	}

	/** {@return how many functions were charged to each bucket} */
	public Map<Bucket, Integer> functions() {
		return Map.copyOf(functions);
	}

	/** {@return the largest functions by size, biggest first} */
	public Map<String, Long> largest() {
		return Map.copyOf(largest);
	}

	/** {@return the total size of the code section in bytes} */
	public long total() {
		return bytes.values().stream().mapToLong(Long::longValue).sum();
	}

	/**
	 * Reads a module and attributes its code section.
	 *
	 * @param wasm the module to read
	 * @param userPackage the package prefix the program under measurement lives in
	 * @return the split
	 * @throws IOException if the module cannot be read
	 */
	public static ModuleSplit of(Path wasm, String userPackage) throws IOException {
		return of(Files.readAllBytes(wasm), userPackage);
	}

	/**
	 * Attributes a module's code section.
	 *
	 * @param wasm the module bytes
	 * @param userPackage the package prefix the program under measurement lives in
	 * @return the split
	 */
	public static ModuleSplit of(byte[] wasm, String userPackage) {
		Wasm reader = new Wasm(wasm);
		Map<Integer, String> names = reader.functionNames();
		Map<Integer, Integer> sizes = reader.functionBodySizes();

		Map<Bucket, Long> bytes = new LinkedHashMap<>();
		Map<Bucket, Integer> functions = new LinkedHashMap<>();
		for (Bucket b : Bucket.values()) {
			bytes.put(b, 0L);
			functions.put(b, 0);
		}

		TreeMap<Long, String> ranked = new TreeMap<>();
		for (Map.Entry<Integer, Integer> e : sizes.entrySet()) {
			String name = names.get(e.getKey());
			Bucket bucket = classify(name, userPackage);
			bytes.merge(bucket, (long) e.getValue(), Long::sum);
			functions.merge(bucket, 1, Integer::sum);
			if (name != null) ranked.put((long) e.getValue(), name);
		}

		Map<String, Long> largest = new LinkedHashMap<>();
		ranked
			.descendingMap()
			.entrySet()
			.stream()
			.limit(15)
			.forEach(e -> largest.put(e.getValue(), e.getKey()));

		return new ModuleSplit(bytes, functions, largest);
	}

	private static Bucket classify(String name, String userPackage) {
		if (name == null) return Bucket.UNNAMED;
		if (!userPackage.isEmpty() && name.startsWith(userPackage)) return Bucket.USER_CODE;
		if (name.startsWith("java.") || name.startsWith("javax.")) return Bucket.CLASS_LIBRARY;
		if (name.startsWith("org.teavm.classlib")) return Bucket.CLASS_LIBRARY;
		if (name.startsWith("kotlin")) return Bucket.CLASS_LIBRARY;
		return Bucket.RUNTIME;
	}

	/** {@return a table, one row per bucket} */
	@Override
	public String toString() {
		long total = total();
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-14s %10s %8s %7s%n", "bucket", "bytes", "funcs", "share"));
		for (Bucket b : Bucket.values()) {
			long v = bytes.getOrDefault(b, 0L);
			if (v == 0) continue;
			sb.append(
				String.format(
					"%-14s %10d %8d %6.1f%%%n",
					b.name().toLowerCase(),
					v,
					functions.getOrDefault(b, 0),
					total == 0 ? 0.0 : (100.0 * v) / total
				)
			);
		}
		sb.append(String.format("%-14s %10d%n", "total", total));
		return sb.toString();
	}

	/** A reader for the parts of the WebAssembly binary format this measurement needs. */
	private static final class Wasm {

		private final byte[] d;
		private int p;

		Wasm(byte[] d) {
			this.d = d;
		}

		private int u32() {
			int r = 0;
			int s = 0;
			while (true) {
				int b = d[p++] & 0xFF;
				r |= (b & 0x7F) << s;
				if ((b & 0x80) == 0) return r;
				s += 7;
			}
		}

		/** Skips a length-prefixed name. */
		private void skipName() {
			int len = u32();
			p += len;
		}

		/** Skips one value type. A garbage-collected reference type carries a heap type after it. */
		private void valType() {
			int b = d[p++] & 0xFF;
			if (b == 0x63 || b == 0x64) s33();
		}

		/** Skips a limits record: a flags byte, a minimum, and a maximum when the flag is set. */
		private void limits() {
			int flags = d[p++] & 0xFF;
			u32();
			if ((flags & 1) != 0) u32();
		}

		/** Skips a signed LEB128 heap type index. */
		private void s33() {
			while ((d[p++] & 0x80) != 0) {
				// the continuation bit is the whole condition
			}
		}

		/** {@return function index to name, read from the name section's function subsection} */
		Map<Integer, String> functionNames() {
			Map<Integer, String> names = new LinkedHashMap<>();
			forEachSection((id, start, end) -> {
				if (id != 0) return;
				p = start;
				int len = u32();
				String section = new String(d, p, len, StandardCharsets.UTF_8);
				p += len;
				if (!section.equals("name")) return;
				while (p < end) {
					int sub = d[p++] & 0xFF;
					int size = u32();
					int subEnd = p + size;
					if (sub == 1) {
						int count = u32();
						for (int i = 0; i < count; i++) {
							int idx = u32();
							int n = u32();
							names.put(idx, new String(d, p, n, StandardCharsets.UTF_8));
							p += n;
						}
					}
					p = subEnd;
				}
			});
			return names;
		}

		/** {@return function index to body size, offset past the imported functions} */
		Map<Integer, Integer> functionBodySizes() {
			int[] imported = { 0 };
			forEachSection((id, start, end) -> {
				if (id != 2) return;
				p = start;
				int n = u32();
				for (int i = 0; i < n; i++) {
					// `p += u32()` would read p before u32 advanced it, discarding the length prefix
					skipName();
					skipName();
					int kind = d[p++] & 0xFF;
					switch (kind) {
						case 0 -> {
							imported[0]++;
							u32();
						}
						case 1 -> {
							valType();
							limits();
						}
						case 2 -> limits();
						case 3 -> {
							valType();
							p++;
						}
						case 4 -> {
							p++;
							u32();
						}
						default -> throw new IllegalStateException("unknown import kind " + kind);
					}
				}
			});

			Map<Integer, Integer> sizes = new LinkedHashMap<>();
			forEachSection((id, start, end) -> {
				if (id != 10) return;
				p = start;
				int n = u32();
				for (int i = 0; i < n; i++) {
					int before = p;
					int body = u32();
					sizes.put(imported[0] + i, body + (p - before));
					p += body;
				}
			});
			return sizes;
		}

		private void forEachSection(Section visitor) {
			int cursor = 8;
			while (cursor < d.length) {
				p = cursor;
				int id = d[p++] & 0xFF;
				int size = u32();
				int start = p;
				int end = start + size;
				visitor.visit(id, start, end);
				cursor = end;
			}
		}

		private interface Section {
			void visit(int id, int start, int end);
		}
	}
}
