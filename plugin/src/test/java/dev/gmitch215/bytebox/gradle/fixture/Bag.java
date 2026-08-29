package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.io.SerialType;
import java.io.Serializable;

/** Every member shape the format can carry: primitives, boxes, String, arrays, another type. */
@SerialType
public class Bag implements Serializable {

	private static final long serialVersionUID = 90210L;

	/** A boolean. */
	public boolean flag;

	/** A byte. */
	public byte tiny;

	/** A char. */
	public char letter;

	/** A short. */
	public short small;

	/** An int. */
	public int count;

	/** A long. */
	public long big;

	/** A float. */
	public float ratio;

	/** A double. */
	public double precise;

	/** A boxed int, which crosses as an object rather than as a primitive slot. */
	public Integer boxed;

	/** Text. */
	public String label;

	/** An array of primitives. */
	public byte[] payload;

	/** An array of strings. */
	public String[] words;

	/** Another annotated type. */
	public Ident owner;

	private int hidden;

	/** Deserialization builds the instance before it has any fields. */
	public Bag() {}

	/** {@return the hidden value} */
	public int getHidden() {
		return hidden;
	}

	/** @param hidden the hidden value */
	public void setHidden(int hidden) {
		this.hidden = hidden;
	}
}
