package dev.gmitch215.bytebox.io;

import java.io.Serializable;
import java.util.List;

/**
 * The fixtures, and a hand-written codec for each.
 *
 * <p>Every codec here is written the way the Gradle plugin generates one, so this file doubles as the
 * specification the generator is checked against. Nothing reflects: the field list, its order, and
 * every type signature are literals, which is what a generated codec has.
 */
final class Wires {

	private Wires() {}

	static final byte FLAGS = SerialDescriptor.SERIALIZABLE;

	// #region fixtures

	record Order(String sku, int quantity, long total) implements Serializable {}

	record Scalars(
		boolean a,
		byte b,
		char c,
		short d,
		int e,
		long f,
		float g,
		double h
	) implements Serializable {}

	record Boxed(
		Integer count,
		Long total,
		Boolean ready,
		Character grade
	) implements Serializable {}

	record Blobs(byte[] raw, int[] counts, String[] tags) implements Serializable {}

	record Tagged(Status status, String note) implements Serializable {}

	enum Status implements Serializable {
		OPEN,
		CLOSED
	}

	/** A class rather than a record, so the no-argument constructor path is exercised. */
	static final class Node implements Serializable {

		private static final long serialVersionUID = 7L;

		String name;
		Node next;

		Node() {}

		Node(String name, Node next) {
			this.name = name;
			this.next = next;
		}
	}

	// #endregion

	// #region codecs

	static final SerialCodec<Order> ORDER = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Order",
					0L,
					FLAGS,
					List.of(
						new SerialField("quantity", 'I', null),
						new SerialField("total", 'J', null),
						new SerialField("sku", 'L', "Ljava/lang/String;")
					)
				)
			);
		}

		@Override
		public void writeData(Order value, SerialSink sink) {
			sink.writeInt(value.quantity());
			sink.writeLong(value.total());
			sink.writeObject(value.sku());
		}

		@Override
		public Order readData(SerialSource source) {
			int quantity = source.readInt();
			long total = source.readLong();
			String sku = source.readObject(String.class);
			return new Order(sku, quantity, total);
		}
	};

	static final SerialCodec<Scalars> SCALARS = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Scalars",
					0L,
					FLAGS,
					List.of(
						new SerialField("a", 'Z', null),
						new SerialField("b", 'B', null),
						new SerialField("c", 'C', null),
						new SerialField("d", 'S', null),
						new SerialField("e", 'I', null),
						new SerialField("f", 'J', null),
						new SerialField("g", 'F', null),
						new SerialField("h", 'D', null)
					)
				)
			);
		}

		@Override
		public void writeData(Scalars value, SerialSink sink) {
			sink.writeBoolean(value.a());
			sink.writeByte(value.b());
			sink.writeChar(value.c());
			sink.writeShort(value.d());
			sink.writeInt(value.e());
			sink.writeLong(value.f());
			sink.writeFloat(value.g());
			sink.writeDouble(value.h());
		}

		@Override
		public Scalars readData(SerialSource source) {
			return new Scalars(
				source.readBoolean(),
				source.readByte(),
				source.readChar(),
				source.readShort(),
				source.readInt(),
				source.readLong(),
				source.readFloat(),
				source.readDouble()
			);
		}
	};

	static final SerialCodec<Boxed> BOXED = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Boxed",
					0L,
					FLAGS,
					List.of(
						new SerialField("count", 'L', "Ljava/lang/Integer;"),
						new SerialField("grade", 'L', "Ljava/lang/Character;"),
						new SerialField("ready", 'L', "Ljava/lang/Boolean;"),
						new SerialField("total", 'L', "Ljava/lang/Long;")
					)
				)
			);
		}

		@Override
		public void writeData(Boxed value, SerialSink sink) {
			sink.writeObject(value.count());
			sink.writeObject(value.grade());
			sink.writeObject(value.ready());
			sink.writeObject(value.total());
		}

		@Override
		public Boxed readData(SerialSource source) {
			Integer count = source.readObject(Integer.class);
			Character grade = source.readObject(Character.class);
			Boolean ready = source.readObject(Boolean.class);
			Long total = source.readObject(Long.class);
			return new Boxed(count, total, ready, grade);
		}
	};

	static final SerialCodec<Blobs> BLOBS = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Blobs",
					0L,
					FLAGS,
					List.of(
						new SerialField("counts", '[', "[I"),
						new SerialField("raw", '[', "[B"),
						new SerialField("tags", '[', "[Ljava/lang/String;")
					)
				)
			);
		}

		@Override
		public void writeData(Blobs value, SerialSink sink) {
			sink.writeObject(value.counts());
			sink.writeObject(value.raw());
			sink.writeObject(value.tags());
		}

		@Override
		public Blobs readData(SerialSource source) {
			int[] counts = source.readObject(int[].class);
			byte[] raw = source.readObject(byte[].class);
			String[] tags = source.readObject(String[].class);
			return new Blobs(raw, counts, tags);
		}
	};

	static final SerialCodec<Status> STATUS = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Status",
					0L,
					(byte) (SerialDescriptor.SERIALIZABLE | SerialDescriptor.ENUM),
					List.of()
				),
				Wire.ENUM
			);
		}

		@Override
		public void writeData(Status value, SerialSink sink) {
			sink.writeObject(value.name());
		}

		@Override
		public Status readData(SerialSource source) {
			String name = source.readObject(String.class);
			return switch (name) {
				case "OPEN" -> Status.OPEN;
				case "CLOSED" -> Status.CLOSED;
				default -> throw new SerialException(
					name + " is not a constant of dev.gmitch215.bytebox.io.Wires$Status"
				);
			};
		}
	};

	static final SerialCodec<Tagged> TAGGED = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Tagged",
					0L,
					FLAGS,
					List.of(
						new SerialField("note", 'L', "Ljava/lang/String;"),
						new SerialField("status", 'L', "Ldev/gmitch215/bytebox/io/Wires$Status;")
					)
				)
			);
		}

		@Override
		public void writeData(Tagged value, SerialSink sink) {
			sink.writeObject(value.note());
			sink.writeObject(value.status());
		}

		@Override
		public Tagged readData(SerialSource source) {
			String note = source.readObject(String.class);
			Status status = source.readObject(Status.class);
			return new Tagged(status, note);
		}
	};

	static final SerialCodec<Node> NODE = new SerialCodec<>() {
		@Override
		public List<SerialDescriptor> descriptors() {
			return List.of(
				new SerialDescriptor(
					"dev.gmitch215.bytebox.io.Wires$Node",
					7L,
					FLAGS,
					List.of(
						new SerialField("name", 'L', "Ljava/lang/String;"),
						new SerialField("next", 'L', "Ldev/gmitch215/bytebox/io/Wires$Node;")
					)
				)
			);
		}

		@Override
		public void writeData(Node value, SerialSink sink) {
			sink.writeObject(value.name);
			sink.writeObject(value.next);
		}

		@Override
		public Node readData(SerialSource source) {
			Node node = new Node();
			source.claim(node);
			node.name = source.readObject(String.class);
			node.next = source.readObject(Node.class);
			return node;
		}
	};

	// #endregion

	/** Registers every codec, the way the generated registration class does. */
	static void register() {
		Serial.register(Order.class, ORDER);
		Serial.register(Scalars.class, SCALARS);
		Serial.register(Boxed.class, BOXED);
		Serial.register(Blobs.class, BLOBS);
		Serial.register(Status.class, STATUS);
		Serial.register(Tagged.class, TAGGED);
		Serial.register(Node.class, NODE);
	}
}
