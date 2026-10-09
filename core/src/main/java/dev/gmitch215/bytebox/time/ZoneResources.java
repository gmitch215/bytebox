package dev.gmitch215.bytebox.time;

import java.util.Collection;
import org.teavm.extension.spi.resources.ResourcesPolicy;

/**
 * Copies the timezone database into the module when {@link ZoneHistory} is in the program, and
 * only then.
 *
 * <p>Found through {@code META-INF/services}, like the substitution policies. The compiler hands a
 * resources policy the names of the classes the program kept, so a program that never links
 * {@link ZoneHistory} gets no 108 KB data segment.
 *
 * @since 1.0.3
 */
public final class ZoneResources implements ResourcesPolicy {

	@Override
	public String[] supplyResources(Collection<? extends String> availableClassNames) {
		return availableClassNames.contains(ZoneHistory.class.getName())
			? new String[] { ZoneHistory.DATA }
			: new String[0];
	}
}
