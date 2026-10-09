package dev.gmitch215.bytebox.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

@DisplayName("a socket's write buffer")
class SocketTest {

	private static final class Recorder implements Wire {

		final List<String> calls = new ArrayList<>();
		RuntimeException failWrite;

		@Override
		public void write(String text) {
			calls.add("write:" + text);
			if (failWrite != null) throw failWrite;
		}

		@Override
		public String read() {
			calls.add("read");
			return "chunk";
		}

		@Override
		public String readAll() {
			calls.add("readAll");
			return "all";
		}

		@Override
		public String readUntil(String delimiter) {
			calls.add("readUntil:" + delimiter);
			return "line";
		}

		@Override
		public JSSocket startTls() {
			calls.add("startTls");
			return null;
		}

		@Override
		public JSPromise<JSObject> closed() {
			calls.add("closed");
			return null;
		}

		@Override
		public void close() {
			calls.add("close");
		}
	}

	private final Recorder wire = new Recorder();
	private final Socket socket = new Socket(null, wire);

	@Test
	@DisplayName("holds writes until something needs them sent")
	void holds() {
		socket.write("EHLO a\r\n");
		socket.write("MAIL FROM:<a@b.c>\r\n");
		assertEquals(List.of(), wire.calls);
	}

	@Test
	@DisplayName("sends every pending write as one chunk, in order, before a read")
	void flushesBeforeRead() {
		socket.write("EHLO a\r\n");
		socket.write("QUIT\r\n");
		assertEquals("chunk", socket.read());
		assertEquals(List.of("write:EHLO a\r\nQUIT\r\n", "read"), wire.calls);
	}

	@Test
	@DisplayName("sends before readAll, readUntil and close")
	void flushesBeforeEveryOtherCall() {
		socket.write("a");
		socket.readAll();
		socket.write("b");
		socket.readUntil("\r\n");
		socket.write("c");
		socket.close();
		assertEquals(
			List.of("write:a", "readAll", "write:b", "readUntil:\r\n", "write:c", "close"),
			wire.calls
		);
	}

	@Test
	@DisplayName("sends before the upgrade to TLS")
	void flushesBeforeStartTls() {
		socket.write("STARTTLS\r\n");
		socket.startTLS();
		assertEquals(List.of("write:STARTTLS\r\n", "startTls"), wire.calls);
	}

	@Test
	@DisplayName("flush sends on request and sends nothing when empty")
	void explicitFlush() {
		socket.flush();
		socket.write("x");
		socket.flush();
		socket.flush();
		assertEquals(List.of("write:x"), wire.calls);
	}

	@Test
	@DisplayName("a failed write surfaces at the flush and is not sent twice")
	void failureSurfacesLater() {
		wire.failWrite = new IllegalStateException("closed");
		socket.write("x");
		assertThrows(IllegalStateException.class, socket::read);
		wire.failWrite = null;
		socket.read();
		assertEquals(List.of("write:x", "read"), wire.calls);
	}

	@Test
	@DisplayName("close sends the held write before it closes")
	void closeFlushes() {
		socket.write("QUIT\r\n");
		socket.close();
		assertEquals(List.of("write:QUIT\r\n", "close"), wire.calls);
	}

	@Test
	@DisplayName("closed sends the held write, so waiting on it cannot wait on an unsent QUIT")
	void closedFlushes() {
		socket.write("QUIT\r\n");
		socket.closed();
		assertEquals(List.of("write:QUIT\r\n", "closed"), wire.calls);
	}

	@Test
	@DisplayName("unwrap sends the held write, so the raw socket's own writes follow it")
	void unwrapFlushes() {
		socket.write("EHLO a\r\n");
		socket.unwrap();
		assertEquals(List.of("write:EHLO a\r\n"), wire.calls);
	}

	@Test
	@DisplayName("sends when the held text reaches the cap, not before")
	void capFlushes() {
		socket.write("a".repeat(Socket.MAX_PENDING - 1));
		assertEquals(List.of(), wire.calls);
		socket.write("b");
		assertEquals(1, wire.calls.size());
		assertEquals(Socket.MAX_PENDING, wire.calls.get(0).length() - "write:".length());
		socket.write("c");
		socket.read();
		assertEquals(List.of("write:c", "read"), wire.calls.subList(1, 3));
	}

	@Test
	@DisplayName("a single write larger than the cap goes out whole")
	void oversizedWrite() {
		socket.write("x".repeat(Socket.MAX_PENDING * 3));
		assertEquals(1, wire.calls.size());
		assertEquals(Socket.MAX_PENDING * 3, wire.calls.get(0).length() - "write:".length());
	}

	@Test
	@DisplayName("closes the connection even when the last flush fails")
	void closesAfterFailedFlush() {
		wire.failWrite = new IllegalStateException("closed");
		socket.write("x");
		assertThrows(IllegalStateException.class, socket::close);
		assertEquals(List.of("write:x", "close"), wire.calls);
	}
}
