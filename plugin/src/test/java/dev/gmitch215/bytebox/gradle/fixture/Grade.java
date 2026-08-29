package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.io.SerialType;

/** An enum, written as its constant name and read back through a switch. */
@SerialType
public enum Grade {
	/** Passing. */
	PASS,
	/** Failing. */
	FAIL
}
