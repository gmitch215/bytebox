package dev.gmitch215.bytebox.socket;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * The two ends of a WebSocket the Worker itself creates.
 *
 * <p>Accepting a connection means making a pair, keeping the server end, and answering the request
 * with the client end and status 101. The client end is never used from Java: it goes back to
 * whoever asked.
 *
 * {@snippet lang = "java":
 * WebSocketPair pair = WebSocketPair.create();
 * state.acceptWebSocket(pair.server());
 * return Bytebox.upgrade(pair.client());
 *}
 *
 * @since 1.0.0
 */
public final class WebSocketPair {

	private final JSObject pair;

	private WebSocketPair(JSObject pair) {
		this.pair = pair;
	}

	/** {@return a new pair} */
	public static WebSocketPair create() {
		return new WebSocketPair(newPair());
	}

	/** {@return the end that goes back to the client, in a 101 response} */
	public WebSocket client() {
		return end(pair, 0);
	}

	/** {@return the end this Worker keeps} */
	public WebSocket server() {
		return end(pair, 1);
	}

	@JSBody(script = "return new WebSocketPair();")
	private static native JSObject newPair();

	// a WebSocketPair is array-like rather than an object with named ends, so the index is the name
	@JSBody(params = { "pair", "index" }, script = "return pair[index];")
	private static native WebSocket end(JSObject pair, int index);
}
