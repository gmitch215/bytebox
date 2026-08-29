package dev.gmitch215.bytebox.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Prints every declared binding, its resolved name, and the remote resource it points at.
 *
 * <p>The one task here that wraps nothing. It answers "what is this Worker actually bound to" from
 * the declarations rather than from memory, which is where a name that drifted shows up.
 *
 * @since 1.0.0
 */
@DisableCachingByDefault(
	because = "the report goes to the build log, so a cache hit would print nothing"
)
public abstract class BindingsReportTask extends DefaultTask {

	/** {@return the declared bindings} */
	@Input
	public abstract ListProperty<Binding> getBindings();

	/** Prints the report. */
	@TaskAction
	public void report() {
		for (String row : rows(getBindings().get())) getLogger().lifecycle("{}", row);
	}

	/**
	 * The report itself, apart from the log it is written to.
	 *
	 * @param bindings the declared bindings
	 * @return one line per binding, under a header
	 */
	static List<String> rows(List<Binding> bindings) {
		if (bindings.isEmpty()) return List.of("bytebox: no bindings are declared");

		List<String[]> cells = new ArrayList<>();
		cells.add(new String[] { "NAME", "TYPE", "POINTS AT" });
		for (Binding binding : bindings) {
			cells.add(new String[] {
				binding.name(),
				binding.type().name().toLowerCase(Locale.ROOT),
				binding.identifiers().isEmpty()
					? "provisioned by Wrangler"
					: describe(binding.identifiers())
			});
		}

		int[] widths = new int[3];
		for (String[] row : cells) {
			for (int i = 0; i < 3; i++) widths[i] = Math.max(widths[i], row[i].length());
		}
		List<String> rows = new ArrayList<>();
		for (String[] row : cells) {
			rows.add("  " + pad(row[0], widths[0]) + "  " + pad(row[1], widths[1]) + "  " + row[2]);
		}
		return rows;
	}

	private static String describe(Map<String, String> identifiers) {
		List<String> parts = new ArrayList<>();
		identifiers.forEach((key, value) -> parts.add(key + "=" + value));
		return String.join(" ", parts);
	}

	private static String pad(String value, int width) {
		return value + " ".repeat(width - value.length());
	}
}
