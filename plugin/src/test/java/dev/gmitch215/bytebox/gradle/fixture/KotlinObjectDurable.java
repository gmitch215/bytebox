package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.durable.DurableObject;

/** A Durable Object declared as a Kotlin {@code object}, which cannot be one instance per id. */
public final class KotlinObjectDurable implements DurableObject {

	public static final KotlinObjectDurable INSTANCE = new KotlinObjectDurable();

	private KotlinObjectDurable() {}
}
