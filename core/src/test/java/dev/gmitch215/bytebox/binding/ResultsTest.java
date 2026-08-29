package dev.gmitch215.bytebox.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.core.JSArrayReader;

/**
 * What a listing or a result hands back as Java, which is the part of a binding that is not a call
 * out to the platform. Everything that reads or writes is a promise and belongs to the workerd lane.
 */
@DisplayName("what a binding hands back")
class ResultsTest {

	@Test
	@DisplayName("names the keys in a KV page")
	void kvListing() {
		KVNamespace.Listing listing = new KVNamespace.Listing() {
			@Override
			public JSArrayReader<KVNamespace.Key> getKeys() {
				return reader(List.of(key("a"), key("b")));
			}

			@Override
			public boolean isListComplete() {
				return true;
			}

			@Override
			public String getCursor() {
				return null;
			}
		};

		assertEquals(List.of("a", "b"), listing.names());
	}

	@Test
	@DisplayName("reads a KV entry that holds nothing as null rather than failing")
	void kvEntry() {
		KVNamespace.Entry entry = new KVNamespace.Entry() {
			@Override
			public org.teavm.jso.core.JSString getValue() {
				return null;
			}

			@Override
			public TSObject getMetadata() {
				return null;
			}
		};

		assertNull(entry.value());
	}

	@Test
	@DisplayName("names the keys in an R2 page")
	void r2Listing() {
		R2Bucket.R2Objects page = new R2Bucket.R2Objects() {
			@Override
			public JSArrayReader<R2Bucket.R2Object> getObjects() {
				return reader(List.of(object("one.txt"), object("two.txt")));
			}

			@Override
			public boolean isTruncated() {
				return false;
			}

			@Override
			public String getCursor() {
				return null;
			}
		};

		assertEquals(List.of("one.txt", "two.txt"), page.keys());
	}

	@Test
	@DisplayName("reads a D1 result's rows and the count of what it changed")
	void d1Result() {
		TSObject row = new Row();

		assertEquals(List.of(row), result(reader(List.of(row)), 4).rows());
		assertEquals(4, result(reader(List.of(row)), 4).changes());
	}

	@Test
	@DisplayName("reads a D1 result that carries neither rows nor a meta block")
	void emptyD1Result() {
		assertEquals(List.of(), result(null, -1).rows());
		assertEquals(0, result(null, -1).changes());
	}

	@Test
	@DisplayName(
		"binds a null the way SQLite wants it, which is null rather than a JavaScript value"
	)
	void bindsNull() {
		assertNull(Values.toJs(null));
	}

	private static D1Database.D1Result result(JSArrayReader<TSObject> rows, int changes) {
		D1Database.D1Meta meta =
			changes < 0
				? null
				: new D1Database.D1Meta() {
						@Override
						public int getChanges() {
							return changes;
						}

						@Override
						public double getLastRowId() {
							return 0;
						}

						@Override
						public int getRowsRead() {
							return 0;
						}

						@Override
						public int getRowsWritten() {
							return 0;
						}

						@Override
						public double getDuration() {
							return 0;
						}
					};

		return new D1Database.D1Result() {
			@Override
			public JSArrayReader<TSObject> getResults() {
				return rows;
			}

			@Override
			public boolean isSuccess() {
				return true;
			}

			@Override
			public D1Database.D1Meta getMeta() {
				return meta;
			}
		};
	}

	private static KVNamespace.Key key(String name) {
		return new KVNamespace.Key() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public int getExpiration() {
				return 0;
			}

			@Override
			public TSObject getMetadata() {
				return null;
			}
		};
	}

	private static R2Bucket.R2Object object(String key) {
		return new R2Bucket.R2Object() {
			@Override
			public String getKey() {
				return key;
			}

			@Override
			public double getSize() {
				return 0;
			}

			@Override
			public String getEtag() {
				return "";
			}

			@Override
			public String getVersion() {
				return "";
			}

			@Override
			public TSObject getHttpMetadata() {
				return null;
			}

			@Override
			public TSObject getCustomMetadata() {
				return null;
			}
		};
	}

	private static <T> JSArrayReader<T> reader(List<T> values) {
		return new JSArrayReader<>() {
			@Override
			public int getLength() {
				return values.size();
			}

			@Override
			public T get(int index) {
				return values.get(index);
			}
		};
	}

	/** A value that is only ever compared by identity here. */
	private static final class Row implements TSObject {

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
	}
}
