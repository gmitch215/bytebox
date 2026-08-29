package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.json.JSONType;

/** Holds an annotated type inside another class, which the scan used to walk past. */
public final class Nested {

	private Nested() {}

	/** Annotated, and nested, so its binary name carries a dollar sign. */
	@JSONType
	public record Inner(String value) {}
}
