package dev.gmitch215.bytebox.gradle;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.gradle.api.GradleException;

/**
 * Computes a class's {@code serialVersionUID} the way the serialization specification does.
 *
 * <p>Nothing here is a choice. The specification fixes the bytes that are hashed, fixes SHA-1 as the
 * hash, and fixes that the identifier is the first eight bytes of the digest read little-endian. A
 * different hash, or the same hash over different bytes, produces a number a JVM rejects with
 * {@code InvalidClassException}, so there is no speed-against-collisions trade available here.
 *
 * <p>Three shapes skip it. A class that declares its own identifier uses that value verbatim, which is
 * what the specification says and also what anyone maintaining a wire format should do. A record's
 * identifier is 0 by rule rather than computed. So is an enum's.
 *
 * @since 1.0.0
 */
final class SerialVersionUID {

	/** What the specification masks a class's modifiers with. */
	private static final int CLASS_MODIFIERS =
		Modifier.PUBLIC | Modifier.FINAL | Modifier.INTERFACE | Modifier.ABSTRACT;

	/** What it masks a constructor's or method's modifiers with. */
	private static final int MEMBER_MODIFIERS =
		Modifier.PUBLIC |
		Modifier.PRIVATE |
		Modifier.PROTECTED |
		Modifier.STATIC |
		Modifier.FINAL |
		Modifier.SYNCHRONIZED |
		Modifier.NATIVE |
		Modifier.ABSTRACT |
		Modifier.STRICT;

	private SerialVersionUID() {}

	/**
	 * The identifier a stream should carry for a class.
	 *
	 * @param type the class
	 * @param classFile the class's compiled bytes, which is the only place a static initializer is
	 *     visible; {@code null} if they cannot be found, which makes one be assumed absent
	 * @return the declared identifier, or the computed one
	 */
	static long of(Class<?> type, byte[] classFile) {
		Long declared = declared(type);
		if (declared != null) return declared;
		if (type.isRecord() || type.isEnum()) return 0L;
		return computed(type, classFile != null && hasStaticInitializer(classFile));
	}

	/**
	 * A {@code static final serialVersionUID} on the class itself, if there is one.
	 *
	 * <p>Read the way the runtime reads it rather than the way the specification words it: the field
	 * has to be {@code static final}, and any type a {@code long} widens from is accepted, because
	 * that is what {@code Field.getLong} does. A field that is not one of those is not the identifier
	 * at all, and the runtime hashes the class instead of failing — so this does too, or the two would
	 * disagree about a class neither of them refuses.
	 */
	static Long declared(Class<?> type) {
		try {
			Field field = type.getDeclaredField("serialVersionUID");
			int required = Modifier.STATIC | Modifier.FINAL;
			if ((field.getModifiers() & required) != required) return null;
			field.setAccessible(true);
			return field.getLong(null);
		} catch (NoSuchFieldException | IllegalAccessException | RuntimeException notTheField) {
			return null;
		}
	}

	private static long computed(Class<?> type, boolean staticInitializer) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeUTF(type.getName());

			int modifiers = type.getModifiers() & CLASS_MODIFIERS;
			if ((modifiers & Modifier.INTERFACE) != 0) {
				modifiers =
					type.getDeclaredMethods().length > 0
						? modifiers | Modifier.ABSTRACT
						: modifiers & ~Modifier.ABSTRACT;
			}
			out.writeInt(modifiers);

			List<String> interfaces = new ArrayList<>();
			for (Class<?> implemented : type.getInterfaces()) interfaces.add(implemented.getName());
			interfaces.sort(Comparator.naturalOrder());
			for (String implemented : interfaces) out.writeUTF(implemented);

			List<Field> fields = new ArrayList<>(List.of(type.getDeclaredFields()));
			fields.sort(Comparator.comparing(Field::getName));
			for (Field field : fields) {
				int mods = field.getModifiers();
				boolean skipped =
					(mods & Modifier.PRIVATE) != 0 &&
					(mods & (Modifier.STATIC | Modifier.TRANSIENT)) != 0;
				if (skipped) continue;
				out.writeUTF(field.getName());
				out.writeInt(mods);
				out.writeUTF(signature(field.getType()));
			}

			if (staticInitializer) {
				out.writeUTF("<clinit>");
				out.writeInt(Modifier.STATIC);
				out.writeUTF("()V");
			}

			List<Constructor<?>> constructors = new ArrayList<>(
				List.of(type.getDeclaredConstructors())
			);
			constructors.removeIf(constructor -> Modifier.isPrivate(constructor.getModifiers()));
			constructors.sort(
				Comparator.comparing(constructor ->
					signature(constructor.getParameterTypes(), void.class)
				)
			);
			for (Constructor<?> constructor : constructors) {
				out.writeUTF("<init>");
				out.writeInt(constructor.getModifiers() & MEMBER_MODIFIERS);
				out.writeUTF(
					signature(constructor.getParameterTypes(), void.class).replace('/', '.')
				);
			}

			List<Method> methods = new ArrayList<>(List.of(type.getDeclaredMethods()));
			methods.removeIf(method -> Modifier.isPrivate(method.getModifiers()));
			methods.sort(
				Comparator.comparing(Method::getName).thenComparing(method ->
					signature(method.getParameterTypes(), method.getReturnType())
				)
			);
			for (Method method : methods) {
				out.writeUTF(method.getName());
				out.writeInt(method.getModifiers() & MEMBER_MODIFIERS);
				out.writeUTF(
					signature(method.getParameterTypes(), method.getReturnType()).replace('/', '.')
				);
			}
		} catch (IOException impossible) {
			throw new UncheckedIOException(impossible);
		}

		byte[] digest = sha1(bytes.toByteArray());
		long identifier = 0;
		for (int i = Math.min(digest.length, 8) - 1; i >= 0; i--) {
			identifier = (identifier << 8) | (digest[i] & 0xFF);
		}
		return identifier;
	}

	private static byte[] sha1(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-1").digest(input);
		} catch (NoSuchAlgorithmException missing) {
			throw new GradleException(
				"this JVM has no SHA-1, which the serialization format requires",
				missing
			);
		}
	}

	/** The JVM type signature for one type: {@code I}, {@code [B}, {@code Ljava/lang/String;}. */
	static String signature(Class<?> type) {
		if (type.isArray()) return "[" + signature(type.getComponentType());
		if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
		if (type == int.class) return "I";
		if (type == long.class) return "J";
		if (type == short.class) return "S";
		if (type == byte.class) return "B";
		if (type == char.class) return "C";
		if (type == boolean.class) return "Z";
		if (type == float.class) return "F";
		if (type == double.class) return "D";
		return "V";
	}

	private static String signature(Class<?>[] parameters, Class<?> returns) {
		StringBuilder out = new StringBuilder("(");
		for (Class<?> parameter : parameters) out.append(signature(parameter));
		return out.append(')').append(signature(returns)).toString();
	}

	/**
	 * Whether a class file declares a static initializer.
	 *
	 * <p>The one input the computation needs that reflection cannot give: {@code <clinit>} is not a
	 * declared method. The runtime asks the virtual machine; here the class file is read instead. The
	 * constant pool has to be walked rather than searched, because a class that merely mentions the
	 * string {@code <clinit>} in a literal would otherwise be read as having one.
	 */
	static boolean hasStaticInitializer(byte[] classFile) {
		ByteBuffer buffer = ByteBuffer.wrap(classFile);
		if (buffer.remaining() < 10 || buffer.getInt() != 0xCAFEBABE) return false;
		buffer.getShort();
		buffer.getShort();

		int count = buffer.getShort() & 0xFFFF;
		String[] pool = new String[count];
		for (int index = 1; index < count; index++) {
			int tag = buffer.get() & 0xFF;
			switch (tag) {
				case 1 -> {
					int length = buffer.getShort() & 0xFFFF;
					byte[] utf = new byte[length];
					buffer.get(utf);
					pool[index] = new String(utf, java.nio.charset.StandardCharsets.UTF_8);
				}
				case 7, 8, 16, 19, 20 -> buffer.position(buffer.position() + 2);
				case 15 -> buffer.position(buffer.position() + 3);
				case 3, 4, 9, 10, 11, 12, 17, 18 -> buffer.position(buffer.position() + 4);
				case 5, 6 -> {
					buffer.position(buffer.position() + 8);
					// a long or double takes two entries, and the second is unusable
					index++;
				}
				default -> {
					return false;
				}
			}
		}

		buffer.getShort();
		buffer.getShort();
		buffer.getShort();
		int interfaces = buffer.getShort() & 0xFFFF;
		buffer.position(buffer.position() + interfaces * 2);

		int fields = buffer.getShort() & 0xFFFF;
		for (int i = 0; i < fields; i++) skipMember(buffer);

		int methods = buffer.getShort() & 0xFFFF;
		for (int i = 0; i < methods; i++) {
			buffer.getShort();
			int name = buffer.getShort() & 0xFFFF;
			buffer.getShort();
			int attributes = buffer.getShort() & 0xFFFF;
			for (int a = 0; a < attributes; a++) skipAttribute(buffer);
			if (name < pool.length && "<clinit>".equals(pool[name])) return true;
		}
		return false;
	}

	private static void skipMember(ByteBuffer buffer) {
		buffer.position(buffer.position() + 6);
		int attributes = buffer.getShort() & 0xFFFF;
		for (int a = 0; a < attributes; a++) skipAttribute(buffer);
	}

	private static void skipAttribute(ByteBuffer buffer) {
		buffer.getShort();
		int length = buffer.getInt();
		buffer.position(buffer.position() + length);
	}
}
