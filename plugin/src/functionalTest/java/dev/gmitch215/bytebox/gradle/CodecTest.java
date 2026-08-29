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

@DisplayName("generateCodecs")
class CodecTest {

	private static final String HANDLER = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			Order order = request.json(Order.class);
			return Bytebox.json(order, Order.class);
		}
	}
	""";

	@Test
	@DisplayName("writes a codec for an annotated record, and it compiles")
	void writesARecordCodec(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.json.JSONType;
			import java.util.List;

			@JSONType
			public record Order(String sku, int quantity, long total, boolean paid, List<String> tags) {}
			"""
		);

		BuildResult result = Fixtures.runner(root(root, sources), "compileByteboxJava").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateCodecs").getOutcome());
		String codec = Files.readString(
			root
				.resolve("build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated")
				.resolve("fixture_OrderCodec.java")
		);
		assertTrue(codec.contains("new fixture.Order("), codec);
		assertTrue(codec.contains("get(\"sku\").asString()"), codec);
		assertTrue(codec.contains("get(\"quantity\").asInt()"), codec);
		// a long is a BigInt at the boundary, so it needs the long reader rather than the int one
		assertTrue(codec.contains("get(\"total\").asLong()"), codec);
		assertTrue(codec.contains("get(\"paid\").asBoolean()"), codec);
		assertTrue(codec.contains("get(\"tags\").asStringList()"), codec);
	}

	@Test
	@DisplayName("registers every codec it wrote")
	void registersThem(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.json.JSONType;

			@JSONType
			public record Order(String sku, int quantity, long total, boolean paid,
				java.util.List<String> tags) {}
			"""
		);

		Fixtures.runner(root(root, sources), "generateCodecs").build();

		String registry = Files.readString(
			root
				.resolve("build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated")
				.resolve("ByteboxCodecs.java")
		);
		assertTrue(registry.contains("JSON.register(fixture.Order.class"), registry);
	}

	@Test
	@DisplayName("names the field whose type nothing converts")
	void namesAnUnconvertibleField(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", Fixtures.fetchHandler());
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.json.JSONType;

			@JSONType
			public record Order(java.util.regex.Pattern pattern) {}
			"""
		);

		BuildResult result = Fixtures.runner(root(root, sources), "generateCodecs").buildAndFail();

		assertTrue(result.getOutput().contains("Order.pattern"), result.getOutput());
		assertTrue(result.getOutput().contains("nothing converts a Pattern"), result.getOutput());
	}

	@Test
	@DisplayName("writes a codec for a nested annotated type")
	void writesANestedCodec(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", Fixtures.fetchHandler());
		sources.put(
			"fixture/Address.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.json.JSONType;

			@JSONType
			public record Address(String city) {}
			"""
		);
		sources.put(
			"fixture/Customer.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.json.JSONType;

			@JSONType
			public record Customer(String name, Address address) {}
			"""
		);

		Fixtures.runner(root(root, sources), "compileByteboxJava").build();

		String codec = Files.readString(
			root
				.resolve("build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated")
				.resolve("fixture_CustomerCodec.java")
		);
		assertTrue(codec.contains("new fixture_AddressCodec().decode("), codec);
	}

	@Test
	@DisplayName("assigns a public field rather than calling one, so the codec compiles")
	void writesABeanCodec(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.json.JSONType;

			@JSONType
			public class Order {
				public String sku;
				private int quantity;

				public int getQuantity() {
					return quantity;
				}

				public void setQuantity(int quantity) {
					this.quantity = quantity;
				}
			}
			"""
		);

		Fixtures.runner(root(root, sources), "compileByteboxJava").build();

		String codec = Files.readString(
			root
				.resolve("build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated")
				.resolve("fixture_OrderCodec.java")
		);
		assertTrue(codec.contains("decoded.sku = "), codec);
		assertTrue(codec.contains("decoded.setQuantity("), codec);
	}

	private static Path root(Path root, Map<String, String> sources) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
			}
			""",
			sources
		);
		return root;
	}
}
