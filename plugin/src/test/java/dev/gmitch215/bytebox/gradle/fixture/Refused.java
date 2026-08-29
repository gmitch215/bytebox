package dev.gmitch215.bytebox.gradle.fixture;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Shapes a generator has to refuse, each for its own reason.
 *
 * <p>None carry a marker annotation, so the scan walks past them and a test has to name the one it
 * wants. That keeps every other test's scan clean.
 */
@SuppressWarnings("serial")
public final class Refused {

	private Refused() {}

	/** Nothing converts an {@code Instant}. */
	public static class OddJsonField {

		/** Not a type the codec writes. */
		public Instant at;
	}

	/** A codec over no fields would produce an empty object. */
	public static class NoFields {}

	/** Private, with nothing public to read it through. */
	public static class Shy {

		private String secret = "";
	}

	/** Private, readable, with nothing to write it through. */
	public static class Deaf {

		private String value = "";

		/** {@return the value} */
		public String getValue() {
			return value;
		}
	}

	/** A list of something no reader handles. */
	public static class OddList {

		/** A list whose element type has no reader. */
		public List<Instant> moments = List.of();
	}

	/** The format records that a type is serializable, and this one is not. */
	public static class NotSerializable {

		/** A field, so the failure is the interface rather than an empty type. */
		public int value;
	}

	/** Its format is whatever {@code writeExternal} writes. */
	public static class External implements Externalizable {

		/** A field. */
		public int value;

		/** Deserialization builds the instance before it has any fields. */
		public External() {}

		@Override
		public void writeExternal(ObjectOutput out) {}

		@Override
		public void readExternal(ObjectInput in) {}
	}

	/** A custom {@code writeObject} puts a class annotation in the stream. */
	public static class Custom implements Serializable {

		/** A field. */
		public int value;

		/** Deserialization builds the instance before it has any fields. */
		public Custom() {}

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.defaultWriteObject();
		}

		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
			in.defaultReadObject();
		}
	}

	/** Nothing can build one without a constructor to call. */
	public static class NoConstructor implements Serializable {

		/** A field. */
		public int value;

		/** @param value the value */
		public NoConstructor(int value) {
			this.value = value;
		}
	}

	/** A collection's format is its own private {@code writeObject} rather than its fields. */
	public static class OddSerialField implements Serializable {

		/** Not a type the format writes here. */
		public List<String> items = List.of();

		/** Deserialization builds the instance before it has any fields. */
		public OddSerialField() {}
	}

	/** Private, with nothing public to read it through. */
	public static class SerialShy implements Serializable {

		private int value;

		/** Deserialization builds the instance before it has any fields. */
		public SerialShy() {}
	}

	/** Private, readable, with nothing to write it through. */
	public static class SerialDeaf implements Serializable {

		private int value;

		/** Deserialization builds the instance before it has any fields. */
		public SerialDeaf() {}

		/** {@return the value} */
		public int getValue() {
			return value;
		}
	}
}
