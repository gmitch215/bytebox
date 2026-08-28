package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The identifier, checked against the runtime that defines it.
 *
 * <p>The specification fixes the bytes hashed, the hash, and how the digest becomes a long, so this is
 * checkable rather than assertable: every fixture's computed identifier is compared against what
 * {@code ObjectStreamClass.lookup} reports on this JVM. A test that only round-tripped our own output
 * would agree with itself while disagreeing with every JVM in the world.
 */
// the fixtures declare no identifier on purpose: computing one for a class that has none is the point
@SuppressWarnings("serial")
class SerialVersionUIDTest {

	// #region fixtures

	static class Plain implements Serializable {

		public String name;
		int count;
		private long hidden;
		private static String ignored;
		private transient String alsoIgnored;

		public Plain() {}

		public Plain(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		protected void touch() {}

		private void invisible() {}
	}

	static class WithClinit implements Serializable {

		static final List<String> TABLE = List.of("a", "b");

		public int value;
	}

	static class WithoutClinit implements Serializable {

		public int value;
	}

	static final class Declared implements Serializable {

		private static final long serialVersionUID = -424242L;

		public int value;
	}

	static class Subclass extends Plain {

		public double extra;

		public Subclass() {}
	}

	interface Contract extends Serializable {
		void act();
	}

	record Component(String sku, int quantity) implements Serializable {}

	enum Flag implements Serializable {
		ON,
		OFF
	}

	// #endregion

	@Test
	void aPlainClassMatchesTheRuntime() {
		assertMatches(Plain.class);
	}

	@Test
	void aSubclassMatchesTheRuntime() {
		assertMatches(Subclass.class);
	}

	@Test
	void aClassWithAStaticInitializerMatchesTheRuntime() {
		assertTrue(
			SerialVersionUID.hasStaticInitializer(classFile(WithClinit.class)),
			"a static field with a non-constant initializer compiles to a <clinit>"
		);
		assertMatches(WithClinit.class);
	}

	@Test
	void aClassWithoutOneMatchesTheRuntime() {
		assertFalse(SerialVersionUID.hasStaticInitializer(classFile(WithoutClinit.class)));
		assertMatches(WithoutClinit.class);
	}

	@Test
	void anInterfaceMatchesTheRuntime() {
		assertMatches(Contract.class);
	}

	@Test
	void aDeclaredIdentifierWinsOverTheComputedOne() {
		assertEquals(-424242L, SerialVersionUID.of(Declared.class, classFile(Declared.class)));
		assertEquals(-424242L, uid(Declared.class));
	}

	@Test
	void aRecordIsZeroByRuleRatherThanHashed() {
		assertEquals(0L, SerialVersionUID.of(Component.class, classFile(Component.class)));
		assertEquals(0L, uid(Component.class));
	}

	@Test
	void anEnumIsZeroToo() {
		assertEquals(0L, SerialVersionUID.of(Flag.class, classFile(Flag.class)));
		assertEquals(0L, uid(Flag.class));
	}

	@Test
	void findsADeclaredIdentifierAndNotAnAbsentOne() {
		assertEquals(-424242L, SerialVersionUID.declared(Declared.class));
		assertNull(SerialVersionUID.declared(Plain.class));
	}

	/** With no class file the initializer is assumed absent, which is wrong for a class that has one. */
	@Test
	void aMissingClassFileChangesTheAnswerForAClassThatHasAnInitializer() {
		assertEquals(uid(WithoutClinit.class), SerialVersionUID.of(WithoutClinit.class, null));
		org.junit.jupiter.api.Assertions.assertNotEquals(
			uid(WithClinit.class),
			SerialVersionUID.of(WithClinit.class, null)
		);
	}

	@Test
	void refusesBytesThatAreNotAClassFile() {
		assertFalse(SerialVersionUID.hasStaticInitializer(new byte[] { 1, 2, 3 }));
		assertFalse(SerialVersionUID.hasStaticInitializer(new byte[0]));
	}

	@Test
	void writesTheSignatureTheFormatUses() {
		assertEquals("I", SerialVersionUID.signature(int.class));
		assertEquals("J", SerialVersionUID.signature(long.class));
		assertEquals("Z", SerialVersionUID.signature(boolean.class));
		assertEquals("V", SerialVersionUID.signature(void.class));
		assertEquals("[B", SerialVersionUID.signature(byte[].class));
		assertEquals("[[I", SerialVersionUID.signature(int[][].class));
		assertEquals("Ljava/lang/String;", SerialVersionUID.signature(String.class));
		assertEquals("[Ljava/lang/String;", SerialVersionUID.signature(String[].class));
	}

	private static void assertMatches(Class<?> type) {
		assertEquals(
			uid(type),
			SerialVersionUID.of(type, classFile(type)),
			type.getName() + " computes a different identifier than this JVM"
		);
	}

	private static long uid(Class<?> type) {
		ObjectStreamClass descriptor = ObjectStreamClass.lookup(type);
		assertNotNull(descriptor, type.getName() + " is not serializable");
		return descriptor.getSerialVersionUID();
	}

	private static byte[] classFile(Class<?> type) {
		String path = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream in = SerialVersionUIDTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "could not find " + path + " on the test classpath");
			return in.readAllBytes();
		} catch (IOException unreadable) {
			throw new UncheckedIOException(unreadable);
		}
	}
}
