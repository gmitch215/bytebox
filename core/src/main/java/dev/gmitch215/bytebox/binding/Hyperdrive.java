package dev.gmitch215.bytebox.binding;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * A Hyperdrive configuration. Declared with {@code hyperdrive()}, named {@code HYPERDRIVE} by
 * default.
 *
 * <p>A connection pooler in front of a Postgres or MySQL database, reached with an ordinary driver
 * over {@code cloudflare:sockets}. This binding hands out the connection details; it does not speak
 * the wire protocol itself, so a driver still has to.
 *
 * <p>{@link #getConnectionString()} points at Hyperdrive rather than at the database, which is what
 * makes the pooling and the query cache apply. Connecting to the origin directly skips both.
 *
 * @since 1.0.0
 */
public interface Hyperdrive extends JSObject {
	/** {@return the connection string to hand a driver} */
	@JSProperty
	String getConnectionString();

	/** {@return the host to connect to} */
	@JSProperty
	String getHost();

	/** {@return the port to connect to} */
	@JSProperty
	int getPort();

	/** {@return the user to authenticate as} */
	@JSProperty
	String getUser();

	/** {@return the password to authenticate with} */
	@JSProperty
	String getPassword();

	/** {@return the database name} */
	@JSProperty
	String getDatabase();
}
