package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A program using {@code java.net} compiled to WebAssembly.
 *
 * <p>The claim is that a reference to {@code java.net.Socket} resolves to bytebox's, so a library that
 * opens a socket the ordinary way works unchanged. The only way to check that is to compile a program
 * that uses it, which is what this does: the compiler either substitutes the class or reports an
 * unresolved reference, and nothing in between.
 */
@DisplayName("java.net")
class JavaNetTest {

	private static final String HANDLER = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;
	import java.io.IOException;
	import java.io.InputStream;
	import java.io.OutputStream;
	import java.net.Socket;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			try (Socket socket = new Socket("example.com", 80)) {
				OutputStream out = socket.getOutputStream();
				out.write("GET / HTTP/1.0\\r\\nHost: example.com\\r\\n\\r\\n".getBytes());
				InputStream in = socket.getInputStream();
				byte[] head = new byte[64];
				int read = in.read(head, 0, head.length);
				return Bytebox.response(new String(head, 0, Math.max(read, 0)));
			} catch (IOException failed) {
				return Bytebox.response(failed.getMessage(), 502);
			}
		}
	}
	""";

	@Test
	@DisplayName("substitutes java.net.Socket, so a program using it compiles")
	void compilesAProgramUsingASocket(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);

		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "netsocket"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			sources
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWasmGC").getOutcome());
		assertTrue(
			Files.exists(root.resolve("build/bytebox/worker/src/app.wasmbin")),
			"the module should have been written"
		);
	}

	private static final String HTTP = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;
	import java.io.IOException;
	import java.io.InputStream;
	import java.io.OutputStream;
	import java.net.HttpURLConnection;
	import java.net.InetAddress;
	import java.net.URI;
	import java.net.URL;
	import java.net.UnknownHostException;
	import java.net.http.HttpClient;
	import java.net.http.HttpRequest;
	import java.net.http.HttpResponse;
	import java.time.Duration;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			try {
				URL url = new URL("https://example.com/things?q=1");
				HttpURLConnection legacy = (HttpURLConnection) url.openConnection();
				legacy.setRequestMethod("POST");
				legacy.setRequestProperty("Content-Type", "application/json");
				legacy.setDoOutput(true);
				legacy.setConnectTimeout(2000);
				try (OutputStream body = legacy.getOutputStream()) {
					body.write("{}".getBytes());
				}
				int code = legacy.getResponseCode();
				try (InputStream in = legacy.getInputStream()) {
					in.readAllBytes();
				}
				legacy.disconnect();

				HttpResponse<String> answer = HttpClient.newBuilder()
					.followRedirects(HttpClient.Redirect.NORMAL)
					.connectTimeout(Duration.ofSeconds(5))
					.build()
					.send(
						HttpRequest.newBuilder(URI.create("https://example.com"))
							.header("Accept", "text/html")
							.POST(HttpRequest.BodyPublishers.ofString("hello"))
							.build(),
						HttpResponse.BodyHandlers.ofString()
					);

				InetAddress address = InetAddress.getByName("93.184.216.34");
				return Bytebox.response(
					code + " " + answer.statusCode() + " " + address.getHostAddress()
				);
			} catch (UnknownHostException unknown) {
				return Bytebox.response("no such host", 502);
			} catch (IOException | InterruptedException failed) {
				return Bytebox.response(String.valueOf(failed.getMessage()), 502);
			}
		}
	}
	""";

	@Test
	@DisplayName("substitutes the HTTP stack, so a program using either client compiles")
	void compilesAProgramMakingRequests(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HTTP);

		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "nethttp"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			sources
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWasmGC").getOutcome());
		assertTrue(
			Files.exists(root.resolve("build/bytebox/worker/src/app.wasmbin")),
			"the module should have been written"
		);
	}

	@Test
	@DisplayName("leaves a server socket unresolved, because the platform accepts no connection")
	void refusesAServerSocket(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put(
			"fixture/Handler.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.Bytebox;
			import dev.gmitch215.bytebox.Env;
			import dev.gmitch215.bytebox.ExecutionCtx;
			import dev.gmitch215.bytebox.Request;
			import dev.gmitch215.bytebox.Response;
			import dev.gmitch215.bytebox.Worker;
			import java.io.IOException;
			import java.net.ServerSocket;

			public class Handler implements Worker {
				@Override
				public Response fetch(Request request, Env env, ExecutionCtx ctx) {
					try (ServerSocket server = new ServerSocket(8080)) {
						return Bytebox.response(String.valueOf(server.getLocalPort()));
					} catch (IOException failed) {
						return Bytebox.response("no", 500);
					}
				}
			}
			"""
		);

		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "serversocket"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			sources
		);

		// it fails while being built rather than throwing once running, which is where it should fail
		BuildResult result = Fixtures.runner(root, "buildWorker").buildAndFail();

		assertTrue(
			result.getOutput().contains("ServerSocket") || result.getOutput().contains("java.net"),
			result.getOutput()
		);
	}
}
