package dev.gmitch215.bytebox.binding;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * Which version of the Worker is running. Declared with {@code versionMetadata()}, named
 * {@code CF_VERSION_METADATA}.
 *
 * <p>Readable during module evaluation, because it is a value rather than a service.
 *
 * @since 1.0.0
 */
public interface VersionMetadata extends JSObject {
	/** {@return the version id} */
	@JSProperty
	String getId();

	/** {@return the version tag, when one was set on upload} */
	@JSProperty
	String getTag();

	/** {@return when the version was uploaded, as an ISO 8601 timestamp} */
	@JSProperty
	String getTimestamp();
}
