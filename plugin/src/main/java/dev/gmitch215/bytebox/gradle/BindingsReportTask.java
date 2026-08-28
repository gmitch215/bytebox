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
		List<Binding> bindings = getBindings().get();
		if (bindings.isEmpty()) {
			getLogger().lifecycle("bytebox: no bindings are declared");
			return;
		}

		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "NAME", "TYPE", "POINTS AT" });
		for (Binding binding : bindings) {
			rows.add(new String[] {
				binding.name(),
				binding.type().name().toLowerCase(Locale.ROOT),
				binding.identifiers().isEmpty()
					? "provisioned by Wrangler"
					: describe(binding.identifiers())
			});
		}

		int[] widths = new int[3];
		for (String[] row : rows) {
			for (int i = 0; i < 3; i++) widths[i] = Math.max(widths[i], row[i].length());
		}
		for (String[] row : rows) {
			getLogger().lifecycle(
				"  {}  {}  {}",
				pad(row[0], widths[0]),
				pad(row[1], widths[1]),
				row[2]
			);
		}
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
