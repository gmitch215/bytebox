package dev.gmitch215.bytebox.gradle;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One declared binding, with the name it resolved to.
 *
 * <p>Identifiers are optional throughout. Wrangler provisions a resource it cannot find, so a
 * project that has not created its KV namespace yet still builds and deploys; an identifier is for
 * pointing a binding at a resource whose remote name differs from the binding's.
 *
 * @param type what kind of binding this is
 * @param name the binding name, which is what {@code env} is keyed by
 * @param identifiers the Wrangler keys for this type, in the order Wrangler expects them
 * @since 1.0.0
 */
public record Binding(
	BindingType type,
	String name,
	Map<String, String> identifiers
) implements Serializable {
	/**
	 * @param type what kind of binding this is
	 * @param name the binding name
	 * @param identifiers the Wrangler keys for this type
	 */
	public Binding {
		identifiers = Map.copyOf(identifiers);
	}

	/**
	 * @param type what kind of binding this is
	 * @param name the binding name
	 */
	public Binding(BindingType type, String name) {
		this(type, name, Map.of());
	}

	/**
	 * This binding as the object Wrangler expects under {@link BindingType#configKey()}.
	 *
	 * <p>The key holding the binding name differs by type — {@code binding} for most, {@code name}
	 * for a Durable Object or a Workflow, {@code queue} for a producer — which is why this is not one
	 * shape with the name substituted.
	 *
	 * @return the entry, with the name key first so the generated configuration reads naturally
	 */
	public Map<String, String> toConfig() {
		Map<String, String> entry = new LinkedHashMap<>();
		entry.put(nameKey(), name);
		entry.putAll(identifiers);
		return entry;
	}

	private String nameKey() {
		return switch (type) {
			case DURABLE_OBJECT, WORKFLOW -> "name";
			default -> "binding";
		};
	}
}
