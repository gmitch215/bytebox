package dev.gmitch215.bytebox.durable;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.socket.WebSocket;

/**
 * A Durable Object that takes WebSockets.
 *
 * <p>The only way several clients reach the same object at once, and the only inbound connection a
 * Worker can hold open. Accept one in {@link DurableObject#fetch} and the messages arrive here.
 *
 * {@snippet lang = "java":
 * @Override
 * public Response fetch(Request request, DurableState state, Env env) {
 * 	WebSocketPair pair = WebSocketPair.create();
 * 	state.acceptWebSocket(pair.server());
 * 	return Bytebox.upgrade(pair.client());
 * }
 *
 * @Override
 * public void message(WebSocket socket, String text, DurableState state, Env env) {
 * 	state.broadcast(text);
 * }
 *}
 *
 * <p>Accepting through {@link DurableState#acceptWebSocket} lets the instance hibernate: the connection
 * stays open with no instance in memory, and the next message builds one again. So the instance's
 * fields may be empty when a message arrives even though the connection is old, and per-connection
 * state belongs on the connection through {@link WebSocket#attach}.
 *
 * @since 1.0.0
 */
public interface SocketObject extends DurableObject {
	/**
	 * A text message arrived.
	 *
	 * @param socket which connection sent it
	 * @param text the message
	 * @param state this instance's storage, alarm and sockets
	 * @param env the bindings
	 */
	void message(WebSocket socket, String text, DurableState state, Env env);

	/**
	 * A binary message arrived.
	 *
	 * <p>The default reads it as UTF-8 and hands it to {@link #message}, which is right for a protocol
	 * that is text in a binary frame and wrong for one that is not.
	 *
	 * @param socket which connection sent it
	 * @param bytes the message
	 * @param state this instance's storage, alarm and sockets
	 * @param env the bindings
	 */
	default void message(WebSocket socket, byte[] bytes, DurableState state, Env env) {
		message(socket, dev.gmitch215.bytebox.builtin.Text.decode(bytes), state, env);
	}

	/**
	 * A connection closed.
	 *
	 * @param socket which connection
	 * @param code the close code
	 * @param reason the close reason
	 * @param clean whether the peer closed properly
	 * @param state this instance's storage, alarm and sockets
	 * @param env the bindings
	 */
	default void closed(
		WebSocket socket,
		int code,
		String reason,
		boolean clean,
		DurableState state,
		Env env
	) {}

	/**
	 * A connection failed.
	 *
	 * @param socket which connection
	 * @param message what went wrong
	 * @param state this instance's storage, alarm and sockets
	 * @param env the bindings
	 */
	default void failed(WebSocket socket, String message, DurableState state, Env env) {}
}
