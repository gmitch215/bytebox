package dev.gmitch215.bytebox.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parsing and serialising are absent here: both go through the platform's own {@code JSON}, so the
 * workerd lane is where those are checked. What lives in Java is the registry and what it does when a
 * type has no codec.
 */
@DisplayName("the JSON codec registry")
class JSONTest {

	private record Point(int x, int y) {}

	private record Unregistered(String value) {}

	private static final Codec<Point> CODEC = new Codec<>() {
		@Override
		public Point decode(TSObject value) {
			return new Point(value.get("x").asInt(), value.get("y").asInt());
		}

		@Override
		public TSObject encode(Point value) {
			return new Fake(Map.of("x", value.x(), "y", value.y()));
		}
	};

	@Test
	@DisplayName("reports whether a type has a codec")
	void handles() {
		JSON.register(Point.class, CODEC);

		assertTrue(JSON.handles(Point.class));
		assertFalse(JSON.handles(Unregistered.class));
	}

	@Test
	@DisplayName("decodes through the registered codec")
	void decodes() {
		JSON.register(Point.class, CODEC);

		assertEquals(new Point(3, 4), JSON.decode(new Fake(Map.of("x", 3, "y", 4)), Point.class));
	}

	@Test
	@DisplayName("encodes through the registered codec")
	void encodes() {
		JSON.register(Point.class, CODEC);

		assertEquals(3, JSON.encode(new Point(3, 4), Point.class).get("x").asInt());
	}

	@Test
	@DisplayName("reads a null value as null rather than calling the codec")
	void decodesNull() {
		JSON.register(Point.class, CODEC);

		assertNull(JSON.decode(null, Point.class));
		assertNull(JSON.decode(new Fake(null), Point.class));
	}

	@Test
	@DisplayName("takes a later registration over an earlier one, so a codec can be replaced")
	void replaces() {
		JSON.register(Point.class, CODEC);
		JSON.register(
			Point.class,
			new Codec<>() {
				@Override
				public Point decode(TSObject value) {
					return new Point(-1, -1);
				}

				@Override
				public TSObject encode(Point value) {
					throw new UnsupportedOperationException();
				}
			}
		);

		assertEquals(new Point(-1, -1), JSON.decode(new Fake(Map.of()), Point.class));
		JSON.register(Point.class, CODEC);
	}

	@Test
	@DisplayName("names the type and the three ways out when nothing is registered for it")
	void unregistered() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
			JSON.decode(new Fake(Map.of()), Unregistered.class)
		);

		assertTrue(failure.getMessage().contains("Unregistered"), failure.getMessage());
		assertTrue(failure.getMessage().contains("@JSONType"), failure.getMessage());
		assertTrue(failure.getMessage().contains("json(mapper)"), failure.getMessage());
	}

	/** Enough of a {@link TSObject} for the registry, which never looks past the members below. */
	private record Fake(Map<String, Integer> values) implements TSObject {
		@Override
		public TSObject get(String name) {
			return new Fake(Map.of("value", values.get(name)));
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
		public boolean isNull() {
			return values == null;
		}

		@Override
		public int asInt() {
			return values.values().iterator().next();
		}

		@Override
		public Map<String, TSObject> asMap() {
			Map<String, TSObject> entries = new LinkedHashMap<>();
			for (String key : values.keySet()) entries.put(key, get(key));
			return entries;
		}

		@Override
		public List<String> keys() {
			return List.copyOf(values.keySet());
		}
	}
}
