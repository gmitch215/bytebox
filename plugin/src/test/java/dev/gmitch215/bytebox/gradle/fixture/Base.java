package dev.gmitch215.bytebox.gradle.fixture;

import java.io.Serializable;

/** Not annotated: the derived type is what carries the annotation, and the chain is read from it. */
public class Base implements Serializable {

	private static final long serialVersionUID = 11L;

	/** Held by the base class, so the format writes it in its own descriptor. */
	public String origin;

	/** Deserialization builds the instance before it has any fields. */
	public Base() {}
}
