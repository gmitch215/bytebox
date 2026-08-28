package dev.gmitch215.bytebox.socket;

import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * One end of a WebSocket.
 *
 * <p>The only connection a client can open to a Worker that is not a single request. A Worker accepts
 * one by answering a request with status 101 and the other half of a pair; a Durable Object accepts one
 * into its own set, which is what lets several clients reach the same object.
 *
 * {@snippet lang = "java":
 * WebSocketPair pair = WebSocketPair.create();
 * state.acceptWebSocket(pair.server());
 * return Bytebox.upgrade(pair.client());
 *}
 *
 * <p>Messages arrive on the object's handler rather than through a listener, because a Worker has no
 * ambient loop to run one in.
 *
 * @since 1.0.0
 */
public interface WebSocket extends JSObject {
	/** Normal closure: the purpose the connection was opened for is finished. */
	int NORMAL = 1000;

	/** The endpoint is going away. */
	int GOING_AWAY = 1001;

	/** A message the endpoint cannot accept, which is the code to use for a protocol error. */
	int UNACCEPTABLE = 1003;

	/** The application's own error, and the highest code an application may send. */
	int APPLICATION_ERROR = 4000;

	/**
	 * Sends text.
	 *
	 * @param message the text
	 */
	@JSMethod("send")
	void send(String message);

	/**
	 * Sends bytes.
	 *
	 * @param message the bytes
	 */
	@JSMethod("send")
	void send(ArrayBuffer message);

	/**
	 * Sends bytes.
	 *
	 * @param message the bytes
	 */
	default void send(byte[] message) {
		send(Bytes.toBuffer(message));
	}

	/**
	 * Sends a value as JSON.
	 *
	 * @param message the value
	 */
	default void sendJson(TSObject message) {
		send(message.toJson());
	}

	/**
	 * Closes the connection.
	 *
	 * @param code why, one of the constants here or an application code from 4000 to 4999
	 * @param reason a description, at most 123 bytes once encoded
	 */
	@JSMethod("close")
	void close(int code, String reason);

	/** Closes the connection normally. */
	default void close() {
		close(NORMAL, "");
	}

	/**
	 * Attaches state that survives hibernation.
	 *
	 * <p>A hibernating Durable Object drops its memory and runs its constructor again when a message
	 * arrives, so anything the handler needs about this connection has to live on the connection. At
	 * most 2 KB once serialised.
	 *
	 * @param value the state
	 */
	@JSMethod("serializeAttachment")
	void attach(TSObject value);

	/** {@return what {@link #attach} stored, or {@code null}} */
	@JSMethod("deserializeAttachment")
	TSObject attachment();

	/**
	 * {@return the ready state}
	 *
	 * <p>0 connecting, 1 open, 2 closing, 3 closed.
	 */
	@JSProperty
	int getReadyState();

	/** {@return whether the connection can still carry a message} */
	default boolean isOpen() {
		return getReadyState() == 1;
	}
}
