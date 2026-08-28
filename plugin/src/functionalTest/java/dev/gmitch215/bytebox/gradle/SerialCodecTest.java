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

@DisplayName("generateSerialCodecs")
class SerialCodecTest {

	private static final String HANDLER = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;
	import dev.gmitch215.bytebox.io.Serial;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			Order order = Serial.decode(new byte[0], Order.class);
			return Bytebox.bytes(Serial.encode(order), "application/octet-stream");
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

			import dev.gmitch215.bytebox.io.SerialType;
			import java.io.Serializable;

			@SerialType
			public record Order(String sku, int quantity, long total) implements Serializable {}
			"""
		);

		BuildResult result = Fixtures.runner(root(root, sources), "compileByteboxJava").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateSerialCodecs").getOutcome());
		String codec = codec(root, "fixture_OrderSerial");

		// primitives before references, each group by name: the order the format fixes, and the same
		// order the conformance test proves against a real ObjectOutputStream
		int quantity = codec.indexOf("new SerialField(\"quantity\", 'I', null)");
		int total = codec.indexOf("new SerialField(\"total\", 'J', null)");
		int sku = codec.indexOf("new SerialField(\"sku\", 'L', \"Ljava/lang/String;\")");
		assertTrue(quantity > 0 && total > quantity && sku > total, codec);

		// a record's identifier is zero by rule rather than hashed
		assertTrue(codec.contains("0L,"), codec);
		assertTrue(codec.contains("return new fixture.Order("), codec);
		assertTrue(codec.contains("sink.writeInt(value.quantity());"), codec);
	}

	@Test
	@DisplayName("writes a codec for an enum, whose constants are names")
	void writesAnEnumCodec(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.io.SerialType;
			import java.io.Serializable;

			@SerialType
			public record Order(String sku, int quantity, long total) implements Serializable {}
			"""
		);
		sources.put(
			"fixture/Status.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.io.SerialType;

			@SerialType
			public enum Status {
				OPEN,
				CLOSED
			}
			"""
		);

		BuildResult result = Fixtures.runner(root(root, sources), "compileByteboxJava").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateSerialCodecs").getOutcome());
		String codec = codec(root, "fixture_StatusSerial");
		assertTrue(codec.contains("SerialDescriptor.JAVA_LANG_ENUM"), codec);
		assertTrue(codec.contains("case \"OPEN\" -> fixture.Status.OPEN;"), codec);
		assertTrue(codec.contains("sink.writeObject(value.name());"), codec);
	}

	@Test
	@DisplayName("registers every codec it wrote")
	void registersEveryCodec(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.io.SerialType;
			import java.io.Serializable;

			@SerialType
			public record Order(String sku, int quantity, long total) implements Serializable {}
			"""
		);

		Fixtures.runner(root(root, sources), "compileByteboxJava").build();

		String registry = codec(root, "ByteboxSerialCodecs");
		assertTrue(
			registry.contains("Serial.register(fixture.Order.class, new fixture_OrderSerial());"),
			registry
		);

		// and the entry point calls it, which is what was missing when the codecs were written but
		// never registered
		String entry = codec(root, "ByteboxEntry");
		assertTrue(entry.contains("ByteboxSerialCodecs.register();"), entry);
		assertTrue(entry.contains("ByteboxCodecs.register();"), entry);
	}

	@Test
	@DisplayName("refuses a type whose shape the format cannot carry")
	void refusesWhatItCannotWrite(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.io.SerialType;
			import java.io.Serializable;
			import java.util.List;

			@SerialType
			public record Order(String sku, int quantity, long total, List<String> tags)
				implements Serializable {}
			"""
		);

		BuildResult result = Fixtures.runner(
			root(root, sources),
			"generateSerialCodecs"
		).buildAndFail();

		assertTrue(
			result.getOutput().contains("nothing writes a java.util.List"),
			result.getOutput()
		);
		assertTrue(result.getOutput().contains("its own private writeObject"), result.getOutput());
	}

	@Test
	@DisplayName("refuses a class it could not construct")
	void refusesAClassWithNoConstructor(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.io.SerialType;
			import java.io.Serializable;

			@SerialType
			public class Order implements Serializable {
				public String sku;

				public Order(String sku) {
					this.sku = sku;
				}
			}
			"""
		);

		BuildResult result = Fixtures.runner(
			root(root, sources),
			"generateSerialCodecs"
		).buildAndFail();

		assertTrue(
			result.getOutput().contains("has no no-argument constructor"),
			result.getOutput()
		);
	}

	@Test
	@DisplayName("refuses a custom writeObject rather than writing a stream that lacks it")
	void refusesACustomWriteObject(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);
		sources.put(
			"fixture/Order.java",
			"""
			package fixture;

			import dev.gmitch215.bytebox.io.SerialType;
			import java.io.IOException;
			import java.io.ObjectOutputStream;
			import java.io.Serializable;

			@SerialType
			public class Order implements Serializable {
				public String sku;

				public Order() {}

				private void writeObject(ObjectOutputStream out) throws IOException {
					out.defaultWriteObject();
				}
			}
			"""
		);

		BuildResult result = Fixtures.runner(
			root(root, sources),
			"generateSerialCodecs"
		).buildAndFail();

		assertTrue(result.getOutput().contains("declares writeObject"), result.getOutput());
	}

	private static Path root(Path root, Map<String, String> sources) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "serial"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			sources
		);
		return root;
	}

	private static String codec(Path root, String name) throws IOException {
		return Files.readString(
			root
				.resolve("build/generated/sources/bytebox/java")
				.resolve("dev/gmitch215/bytebox/generated")
				.resolve(name + ".java")
		);
	}
}
