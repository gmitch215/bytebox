package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.gmitch215.bytebox.js.TSObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * Reading a body needs a promise, so what is checkable here is the status: whether a response is one
 * a caller may go on with, and that the check hands the response straight back when it is.
 */
@DisplayName("Response")
class ResponseTest {

	@Test
	@DisplayName("hands a successful response back so the check can sit in an expression")
	void assertOk() {
		Stub response = new Stub(204);

		assertSame(response, response.assertOk());
	}

	@Test
	@DisplayName("reads the status and the headers as given")
	void basics() {
		Stub response = new Stub(404);

		assertEquals(404, response.getStatus());
		assertEquals("Not Found", response.getStatusText());
	}

	/** Everything below the status needs the platform, so the stub refuses rather than pretends. */
	private record Stub(int status) implements Response {
		@Override
		public int getStatus() {
			return status;
		}

		@Override
		public String getStatusText() {
			return status == 404 ? "Not Found" : "";
		}

		@Override
		public boolean isOk() {
			return status >= 200 && status < 300;
		}

		@Override
		public Headers getHeaders() {
			return null;
		}

		@Override
		public String getUrl() {
			return "";
		}

		@Override
		public boolean isBodyUsed() {
			return false;
		}

		@Override
		public JSPromise<JSString> readText() {
			throw new UnsupportedOperationException("reading a body needs a real promise");
		}

		@Override
		public JSPromise<TSObject> readJson() {
			throw new UnsupportedOperationException("reading a body needs a real promise");
		}

		@Override
		public JSPromise<ArrayBuffer> readBytes() {
			throw new UnsupportedOperationException("reading a body needs a real promise");
		}
	}
}
