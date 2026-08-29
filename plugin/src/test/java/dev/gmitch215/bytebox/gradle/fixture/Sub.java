package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.io.SerialType;

/** Two levels, which the format writes most-derived first and reads base-first. */
@SerialType
public class Sub extends Base {

	private static final long serialVersionUID = 12L;

	/** Held by the derived class. */
	public int depth;

	/** Deserialization builds the instance before it has any fields. */
	public Sub() {}
}
