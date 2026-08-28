package dev.gmitch215.bytebox.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code wrangler { }} block: what goes into the generated configuration.
 *
 * @since 1.0.0
 */
public abstract class WranglerSpec {

	/** {@return the Worker's name} */
	public abstract Property<String> getName();

	/**
	 * {@return the compatibility date}
	 *
	 * <p>Validated at build time for format and for a real calendar date, and checked against the
	 * installed Wrangler by {@code buildWorker}, which is the only thing that knows which dates its
	 * runtime supports.
	 */
	public abstract Property<String> getCompatibilityDate();

	/** {@return the compatibility flags} */
	public abstract ListProperty<String> getCompatibilityFlags();

	/** {@return the routes this Worker answers on} */
	public abstract ListProperty<String> getRoutes();

	/** {@return the Cron Triggers that fire it} */
	public abstract ListProperty<String> getCrons();

	/** {@return the Workers this one sends its logs to} */
	public abstract ListProperty<String> getTailConsumers();

	/** {@return whether to send logs to Cloudflare's observability} */
	public abstract Property<Boolean> getObservability();

	/**
	 * Adds routes.
	 *
	 * @param routes the routes
	 */
	public void routes(String... routes) {
		append(getRoutes(), routes);
	}

	/**
	 * Adds Cron Triggers.
	 *
	 * <p>An account gets 5 on the free plan and 250 on paid, counted across every Worker rather than
	 * per Worker.
	 *
	 * @param crons the cron expressions
	 */
	public void crons(String... crons) {
		append(getCrons(), crons);
	}

	/**
	 * Adds compatibility flags.
	 *
	 * @param flags the flags
	 */
	public void compatibilityFlags(String... flags) {
		append(getCompatibilityFlags(), flags);
	}

	/**
	 * Adds tail consumers.
	 *
	 * @param workers the Workers to send logs to
	 */
	public void tailConsumers(String... workers) {
		append(getTailConsumers(), workers);
	}

	private static void append(ListProperty<String> property, String... values) {
		List<String> all = new ArrayList<>(property.get());
		all.addAll(List.of(values));
		property.set(all);
	}
}
