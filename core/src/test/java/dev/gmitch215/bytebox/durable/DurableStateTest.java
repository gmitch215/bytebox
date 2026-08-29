package dev.gmitch215.bytebox.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.socket.WebSocket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * The sockets an instance holds and what a query hands back, which is the part of the state that is
 * Java. Storage and the alarm both wait on the platform and belong to the workerd lane.
 */
@DisplayName("a Durable Object's own context")
class DurableStateTest {

	@Test
	@DisplayName("reads a query's rows and column names, and an empty result as empty")
	void query() {
		StubSql answered = new StubSql(new Rows(List.of("a", "b")), new Rows(List.of("id")));

		assertEquals(2, answered.rows().size());
		assertEquals(List.of("id"), answered.columns());

		StubSql empty = new StubSql(null, null);
		assertEquals(List.of(), empty.rows());
		assertEquals(List.of(), empty.columns());
	}

	@Test
	@DisplayName("hands back every socket the instance holds, in the order it holds them")
	void sockets() {
		StubSocket first = new StubSocket(1);
		StubSocket second = new StubSocket(1);

		assertEquals(List.of(first, second), state(first, second).sockets());
		assertEquals(List.of(), state().sockets());
	}

	@Test
	@DisplayName("sends to the open sockets and counts them, skipping the ones that are not")
	void broadcast() {
		StubSocket open = new StubSocket(1);
		StubSocket closing = new StubSocket(2);
		StubSocket alsoOpen = new StubSocket(1);

		assertEquals(2, state(open, closing, alsoOpen).broadcast("hello"));

		assertEquals(List.of("hello"), open.sent);
		assertEquals(List.of("hello"), alsoOpen.sent);
		assertTrue(closing.sent.isEmpty());
	}

	@Test
	@DisplayName("closes with the normal code when none is given")
	void closesNormally() {
		StubSocket socket = new StubSocket(1);

		socket.close();

		assertEquals(List.of(WebSocket.NORMAL + ":"), socket.closed);
	}

	private static DurableState state(WebSocket... sockets) {
		List<WebSocket> held = List.of(sockets);
		return new StubState(held);
	}

	/** Only the socket half is answered; anything else here would need the platform. */
	private static final class StubState implements DurableState {

		private final List<WebSocket> held;

		StubState(List<WebSocket> held) {
			this.held = held;
		}

		@Override
		public JSArrayReader<WebSocket> webSockets() {
			return new JSArrayReader<>() {
				@Override
				public int getLength() {
					return held.size();
				}

				@Override
				public WebSocket get(int index) {
					return held.get(index);
				}
			};
		}

		@Override
		public Storage storage() {
			throw new UnsupportedOperationException("storage waits on a promise");
		}

		@Override
		public void acceptWebSocket(WebSocket socket) {
			throw new UnsupportedOperationException("the platform accepts a socket");
		}

		@Override
		public void acceptWebSocket(WebSocket socket, JSObject tags) {
			throw new UnsupportedOperationException("the platform accepts a socket");
		}
	}

	private record StubSql(TSObject array, TSObject names) implements SQL {
		@Override
		public TSObject one() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int rowsRead() {
			return 0;
		}

		@Override
		public int rowsWritten() {
			return 0;
		}

		@Override
		public TSObject asArray() {
			return array;
		}

		@Override
		public TSObject columnNames() {
			return names;
		}
	}

	/** A value that is only ever asked for its elements. */
	private record Rows(List<String> values) implements TSObject {
		@Override
		public TSObject get(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void set(String name, TSObject value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public TSObject at(int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<TSObject> asList() {
			return values
				.stream()
				.map(value -> (TSObject) new Rows(List.of(value)))
				.toList();
		}

		@Override
		public List<String> asStringList() {
			return values;
		}
	}

	private static final class StubSocket implements WebSocket {

		private final int readyState;
		final List<String> sent = new ArrayList<>();
		final List<String> closed = new ArrayList<>();

		StubSocket(int readyState) {
			this.readyState = readyState;
		}

		@Override
		public int getReadyState() {
			return readyState;
		}

		@Override
		public void send(String message) {
			sent.add(message);
		}

		@Override
		public void send(ArrayBuffer message) {
			throw new UnsupportedOperationException("bytes need a real buffer");
		}

		@Override
		public void close(int code, String reason) {
			closed.add(code + ":" + reason);
		}

		@Override
		public void attach(TSObject value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public TSObject attachment() {
			throw new UnsupportedOperationException();
		}
	}
}
