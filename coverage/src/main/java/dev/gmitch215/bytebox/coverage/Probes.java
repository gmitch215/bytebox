package dev.gmitch215.bytebox.coverage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.jso.JSExport;

/**
 * Where an instrumented module records what it executed.
 *
 * <p>This is the runtime half of the coverage lane, and it is compiled into the WebAssembly rather
 * than run beside it. JaCoCo's own runtime cannot be: it reads a configuration resource, registers a
 * shutdown hook and writes a file, none of which exist on this platform. What an offline-instrumented
 * class actually needs is one method handing out a {@code boolean[]} per class and keeping it, which
 * is all this is.
 *
 * <p>{@link #dump()} is exported, so the test lane reads the probes out the same way it calls any
 * other handler.
 *
 * @since 1.0.0
 */
public final class Probes {

	private static final Map<String, boolean[]> HITS = new LinkedHashMap<>();
	private static final Map<String, String> IDS = new LinkedHashMap<>();

	private Probes() {}

	/**
	 * The probe array for one class, created on first ask and kept for the life of the module.
	 *
	 * <p>The signature is fixed by what the instrumented bytecode calls, not chosen here.
	 *
	 * @param classId the identifier JaCoCo computed from the uninstrumented class
	 * @param className the class's internal name
	 * @param probeCount how many probes it was instrumented with
	 * @return the array the class records into
	 */
	public static boolean[] get(long classId, String className, int probeCount) {
		boolean[] hits = HITS.get(className);
		if (hits != null) return hits;
		hits = new boolean[probeCount];
		HITS.put(className, hits);
		// as text, because the identifier is a full 64 bits and reading it back through a JavaScript
		// number would lose the low ones
		IDS.put(className, Long.toString(classId));
		return hits;
	}

	/**
	 * {@return every probe recorded so far, one class per line}
	 *
	 * <p>Each line is the identifier, the class name, and one character per probe. A line-oriented
	 * text form rather than JaCoCo's binary one, because writing that format from here would mean
	 * compiling its writer to WebAssembly to serialise something the JVM side has to parse anyway.
	 */
	@JSExport
	public static String dump() {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, boolean[]> entry : HITS.entrySet()) {
			out.append(IDS.get(entry.getKey())).append(' ').append(entry.getKey()).append(' ');
			for (boolean hit : entry.getValue()) out.append(hit ? '1' : '0');
			out.append('\n');
		}
		return out.toString();
	}
}
