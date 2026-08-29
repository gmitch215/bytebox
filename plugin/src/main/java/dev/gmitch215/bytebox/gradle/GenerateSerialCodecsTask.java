package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes a codec for every type annotated {@code @SerialType}, in Java's own serialization format.
 *
 * <p>The identifier, the field list and its order all come from the class as the specification reads
 * it, so a stream written by a generated codec is the stream a JVM writes. What the format needs and
 * reflection cannot give is whether a class has a static initializer, which is read out of the class
 * file.
 *
 * <p>Anything the format cannot express here is refused with the reason, at build time, rather than
 * written as an approximation.
 *
 * @since 1.0.0
 */
@CacheableTask
public abstract class GenerateSerialCodecsTask extends DefaultTask {

	/** The package the generated codecs live in. */
	public static final String PACKAGE = "dev.gmitch215.bytebox.generated";

	/** The class that registers every generated codec. */
	public static final String REGISTRY = "ByteboxSerialCodecs";

	private static final String ANNOTATION = "dev.gmitch215.bytebox.io.SerialType";

	private static final Map<Class<?>, String> PRIMITIVES = Map.of(
		boolean.class,
		"Z",
		byte.class,
		"B",
		char.class,
		"C",
		short.class,
		"S",
		int.class,
		"I",
		long.class,
		"J",
		float.class,
		"F",
		double.class,
		"D"
	);

	private static final Map<Class<?>, String> BOXES = Map.of(
		Boolean.class,
		"Boolean",
		Byte.class,
		"Byte",
		Character.class,
		"Character",
		Short.class,
		"Short",
		Integer.class,
		"Integer",
		Long.class,
		"Long",
		Float.class,
		"Float",
		Double.class,
		"Double"
	);

	private Class<? extends Annotation> marker;

	/** {@return types to generate codecs for that the scan would not find} */
	@Input
	public abstract ListProperty<String> getTypes();

	/** {@return the project's own compiled classes, which are the ones scanned} */
	@Classpath
	public abstract ConfigurableFileCollection getScanned();

	/** {@return everything needed to resolve those classes, including bytebox itself} */
	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	/** {@return where the generated source goes} */
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	/** Writes the codecs. */
	@TaskAction
	public void generate() {
		Path directory = getOutputDirectory()
			.get()
			.getAsFile()
			.toPath()
			.resolve(PACKAGE.replace('.', '/'));
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		try (URLClassLoader loader = loader()) {
			List<String> registrations = new ArrayList<>();
			Set<String> written = new LinkedHashSet<>();
			for (String name : annotated(loader)) {
				Class<?> type = load(loader, name);
				String codec = codecName(type);
				if (!written.add(codec)) continue;
				Files.writeString(directory.resolve(codec + ".java"), source(type, codec, loader));
				registrations.add(
					"\t\tSerial.register(" +
						type.getCanonicalName() +
						".class, new " +
						codec +
						"());"
				);
			}
			Files.writeString(directory.resolve(REGISTRY + ".java"), registry(registrations));
			if (!registrations.isEmpty()) {
				getLogger().lifecycle(
					"bytebox: generated {} serialization codec(s)",
					registrations.size()
				);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// #region scanning

	private List<String> annotated(URLClassLoader loader) {
		Set<String> names = new LinkedHashSet<>(getTypes().get());
		marker = annotation(loader);
		for (File root : getScanned()) {
			if (!root.isDirectory()) continue;
			Path base = root.toPath();
			try (Stream<Path> walk = Files.walk(base)) {
				for (Path file : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
					String name = base
						.relativize(file)
						.toString()
						.replace(File.separatorChar, '.')
						.replaceAll("\\.class$", "");
					if (name.endsWith("package-info")) continue;
					try {
						Class<?> type = Class.forName(name, false, loader);
						if (type.isSynthetic()) continue;
						if (type.isAnnotationPresent(marker)) names.add(name);
					} catch (ClassNotFoundException | NoClassDefFoundError skip) {
						// a class whose dependencies are absent cannot be one of ours
					}
				}
			} catch (IOException unreadable) {
				throw new UncheckedIOException(unreadable);
			}
		}
		return List.copyOf(names);
	}

	private URLClassLoader loader() {
		List<URL> urls = new ArrayList<>();
		for (File entry : getClasspath().plus(getScanned())) {
			try {
				urls.add(entry.toURI().toURL());
			} catch (MalformedURLException unreachable) {
				throw new GradleException(
					"could not read the classpath entry " + entry,
					unreachable
				);
			}
		}
		return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
	}

	private static Class<?> load(URLClassLoader loader, String name) {
		try {
			return Class.forName(name, false, loader);
		} catch (ClassNotFoundException missing) {
			throw new GradleException("could not find the type " + name, missing);
		}
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Annotation> annotation(URLClassLoader loader) {
		try {
			return (Class<? extends Annotation>) Class.forName(ANNOTATION, false, loader);
		} catch (ClassNotFoundException missing) {
			throw new GradleException(
				"bytebox-core is not on the project's compile classpath, so @SerialType cannot be" +
					" resolved. Add a dependency on dev.gmitch215:bytebox-core.",
				missing
			);
		}
	}

	/** The class file, which is where a static initializer is visible and reflection is not. */
	private byte[] classFile(String binaryName) {
		String path = binaryName.replace('.', '/') + ".class";
		for (File root : getScanned()) {
			File file = new File(root, path);
			if (!file.isFile()) continue;
			try {
				return Files.readAllBytes(file.toPath());
			} catch (IOException unreadable) {
				throw new UncheckedIOException(unreadable);
			}
		}
		return null;
	}

	// #endregion

	// #region emission

	private static String codecName(Class<?> type) {
		return type.getName().replace('.', '_').replace('$', '_') + "Serial";
	}

	private String source(Class<?> type, String codecName, URLClassLoader loader) {
		validate(type);
		List<Level> levels = levels(type);

		StringBuilder out = new StringBuilder();
		out.append("package ").append(PACKAGE).append(";\n\n");
		out.append("import dev.gmitch215.bytebox.io.SerialCodec;\n");
		out.append("import dev.gmitch215.bytebox.io.SerialDescriptor;\n");
		out.append("import dev.gmitch215.bytebox.io.SerialException;\n");
		out.append("import dev.gmitch215.bytebox.io.SerialField;\n");
		out.append("import dev.gmitch215.bytebox.io.SerialSink;\n");
		out.append("import dev.gmitch215.bytebox.io.SerialSource;\n");
		out.append("import java.util.List;\n\n");
		out.append("/** Generated by the bytebox Gradle plugin. Edits are overwritten. */\n");
		out.append("public final class ")
			.append(codecName)
			.append(" implements SerialCodec<")
			.append(type.getCanonicalName())
			.append("> {\n\n");

		out.append("\tprivate static final List<SerialDescriptor> DESCRIPTORS = List.of(\n");
		for (int i = 0; i < levels.size(); i++) {
			out.append(descriptor(levels.get(i)));
			out.append(i == levels.size() - 1 ? "\n" : ",\n");
		}
		if (type.isEnum()) out.append("\t\t, SerialDescriptor.JAVA_LANG_ENUM\n");
		out.append("\t);\n\n");

		out.append("\t@Override\n\tpublic List<SerialDescriptor> descriptors() {\n");
		out.append("\t\treturn DESCRIPTORS;\n\t}\n\n");

		if (type.isEnum()) {
			out.append(enumBody(type));
		} else {
			out.append(writeBody(type, levels));
			out.append('\n');
			out.append(readBody(type, levels));
		}
		out.append("}\n");
		return out.toString();
	}

	private String descriptor(Level level) {
		StringBuilder out = new StringBuilder();
		out.append("\t\tnew SerialDescriptor(\n");
		out.append("\t\t\t\"").append(level.type().getName()).append("\",\n");
		out.append("\t\t\t").append(level.identifier()).append("L,\n");
		out.append("\t\t\t(byte) ").append(level.flags()).append(",\n");
		if (level.members().isEmpty()) {
			out.append("\t\t\tList.of()\n");
		} else {
			out.append("\t\t\tList.of(\n");
			for (int i = 0; i < level.members().size(); i++) {
				Member member = level.members().get(i);
				out.append("\t\t\t\tnew SerialField(\"")
					.append(member.name())
					.append("\", '")
					.append(member.typeCode())
					.append("', ")
					.append(member.signature() == null ? "null" : "\"" + member.signature() + "\"")
					.append(')');
				out.append(i == level.members().size() - 1 ? "\n" : ",\n");
			}
			out.append("\t\t\t)\n");
		}
		out.append("\t\t)");
		return out.toString();
	}

	private String writeBody(Class<?> type, List<Level> levels) {
		StringBuilder out = new StringBuilder();
		out.append("\t@Override\n\tpublic void writeData(")
			.append(type.getCanonicalName())
			.append(" value, SerialSink sink) {\n");
		for (Level level : levels.reversed()) {
			for (Member member : level.members()) {
				out.append("\t\tsink.")
					.append(member.writer())
					.append("(value.")
					.append(member.accessor())
					.append(");\n");
			}
		}
		out.append("\t}\n");
		return out.toString();
	}

	private String readBody(Class<?> type, List<Level> levels) {
		String name = type.getCanonicalName();
		StringBuilder out = new StringBuilder();
		out.append("\t@Override\n\tpublic ")
			.append(name)
			.append(" readData(SerialSource source) {\n");

		if (type.isRecord()) {
			for (Level level : levels.reversed()) {
				for (Member member : level.members()) {
					out.append("\t\t")
						.append(member.declared())
						.append(' ')
						.append(member.local())
						.append(" = source.")
						.append(member.reader())
						.append(";\n");
				}
			}
			out.append("\t\treturn new ").append(name).append("(\n");
			List<Member> components = levels.get(0).declarationOrder();
			for (int i = 0; i < components.size(); i++) {
				out.append("\t\t\t").append(components.get(i).local());
				out.append(i == components.size() - 1 ? "\n" : ",\n");
			}
			out.append("\t\t);\n");
		} else {
			out.append("\t\t").append(name).append(" value = new ").append(name).append("();\n");
			// claimed before any field is read, so a field pointing back at this object resolves
			out.append("\t\tsource.claim(value);\n");
			for (Level level : levels.reversed()) {
				for (Member member : level.members()) {
					out.append("\t\tvalue.")
						.append(member.assign("source." + member.reader()))
						.append(";\n");
				}
			}
			out.append("\t\treturn value;\n");
		}
		out.append("\t}\n");
		return out.toString();
	}

	private String enumBody(Class<?> type) {
		String name = type.getCanonicalName();
		StringBuilder out = new StringBuilder();
		out.append("\t@Override\n\tpublic void writeData(")
			.append(name)
			.append(" value, SerialSink sink) {\n");
		out.append("\t\tsink.writeObject(value.name());\n\t}\n\n");

		out.append("\t@Override\n\tpublic ")
			.append(name)
			.append(" readData(SerialSource source) {\n");
		out.append("\t\tString name = source.readObject(String.class);\n");
		out.append("\t\treturn switch (name) {\n");
		for (Object constant : type.getEnumConstants()) {
			String label = ((Enum<?>) constant).name();
			out.append("\t\t\tcase \"")
				.append(label)
				.append("\" -> ")
				.append(name)
				.append('.')
				.append(label)
				.append(";\n");
		}
		out.append("\t\t\tdefault -> throw new SerialException(\n");
		out.append("\t\t\t\tname + \" is not a constant of ").append(type.getName()).append("\"\n");
		out.append("\t\t\t);\n");
		out.append("\t\t};\n\t}\n");
		return out.toString();
	}

	private String registry(List<String> registrations) {
		StringBuilder out = new StringBuilder();
		out.append("package ").append(PACKAGE).append(";\n\n");
		out.append("import dev.gmitch215.bytebox.io.Serial;\n\n");
		out.append("/** Generated by the bytebox Gradle plugin. Edits are overwritten. */\n");
		out.append("public final class ").append(REGISTRY).append(" {\n\n");
		out.append("\tprivate ").append(REGISTRY).append("() {}\n\n");
		out.append("\t/** Registers every generated codec. Called during module evaluation. */\n");
		out.append("\tpublic static void register() {\n");
		if (registrations.isEmpty()) {
			out.append("\t\t// no types were annotated @SerialType\n");
		} else {
			out.append(String.join("\n", registrations)).append('\n');
		}
		out.append("\t}\n}\n");
		return out.toString();
	}

	// #endregion

	// #region reading the class

	private void validate(Class<?> type) {
		if (!java.io.Serializable.class.isAssignableFrom(type)) {
			throw new GradleException(
				type.getName() +
					" is annotated @SerialType but does not implement Serializable, and the format" +
					" records that it does"
			);
		}
		if (java.io.Externalizable.class.isAssignableFrom(type)) {
			throw new GradleException(
				type.getName() +
					" is Externalizable, whose format is whatever writeExternal writes. That is not" +
					" supported; implement Serializable instead."
			);
		}
		for (String custom : List.of("writeObject", "readObject", "writeReplace", "readResolve")) {
			for (Method method : type.getDeclaredMethods()) {
				if (!method.getName().equals(custom)) continue;
				throw new GradleException(
					type.getName() +
						" declares " +
						custom +
						", which puts a class annotation in the stream that a generated codec does" +
						" not write. Remove it, or register a hand-written codec for this type."
				);
			}
		}
		if (!type.isRecord() && !type.isEnum() && noArgument(type) == null) {
			throw new GradleException(
				type.getName() +
					" has no no-argument constructor. Deserialization has to build the instance and" +
					" this platform has no Unsafe to build one without a constructor, so add one or" +
					" make the type a record."
			);
		}
	}

	private static Constructor<?> noArgument(Class<?> type) {
		for (Constructor<?> constructor : type.getDeclaredConstructors()) {
			if (constructor.getParameterCount() != 0) continue;
			if (Modifier.isPrivate(constructor.getModifiers())) continue;
			return constructor;
		}
		return null;
	}

	/** The class and every serializable class above it, most derived first. */
	private List<Level> levels(Class<?> type) {
		List<Level> levels = new ArrayList<>();
		for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
			if (!java.io.Serializable.class.isAssignableFrom(at)) break;
			levels.add(
				new Level(
					at,
					SerialVersionUID.of(at, classFile(at.getName())),
					flags(at),
					members(at),
					declarationOrder(at)
				)
			);
			if (at.isEnum()) break;
		}
		return levels;
	}

	private static int flags(Class<?> type) {
		int flags = 0x02;
		if (type.isEnum()) flags |= 0x10;
		return flags;
	}

	/** Primitives first, then references, each group by name. The order the format fixes. */
	private List<Member> members(Class<?> type) {
		if (type.isEnum()) return List.of();
		List<Member> members = new ArrayList<>(declarationOrder(type));
		members.sort(
			Comparator.comparing((Member member) -> member.primitive() ? 0 : 1).thenComparing(
				Member::name
			)
		);
		return members;
	}

	/** The fields in the order the class declares them, which is how a record is constructed. */
	private List<Member> declarationOrder(Class<?> type) {
		List<Member> members = new ArrayList<>();
		if (type.isRecord()) {
			for (RecordComponent component : type.getRecordComponents()) {
				members.add(
					member(
						type,
						component.getName(),
						component.getType(),
						component.getName() + "()",
						null
					)
				);
			}
			return members;
		}
		for (Field field : type.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) continue;
			if (field.isSynthetic()) continue;
			boolean open = Modifier.isPublic(modifiers);
			members.add(
				member(
					type,
					field.getName(),
					field.getType(),
					open ? field.getName() : getter(type, field),
					open ? null : setter(type, field)
				)
			);
		}
		return members;
	}

	private Member member(
		Class<?> owner,
		String name,
		Class<?> fieldType,
		String accessor,
		String setter
	) {
		String primitive = PRIMITIVES.get(fieldType);
		if (primitive != null) {
			String capitalised = primitive.equals("Z")
				? "Boolean"
				: capitalise(fieldType.getName());
			return new Member(
				name,
				primitive.charAt(0),
				null,
				fieldType.getName(),
				"write" + capitalised,
				"read" + capitalised + "()",
				accessor,
				setter
			);
		}

		String signature = SerialVersionUID.signature(fieldType);
		String declared = fieldType.getCanonicalName();
		if (
			fieldType == String.class ||
			BOXES.containsKey(fieldType) ||
			supportedArray(fieldType) ||
			fieldType.isAnnotationPresent(marker)
		) {
			char code = fieldType.isArray() ? '[' : 'L';
			return new Member(
				name,
				code,
				signature,
				declared,
				"writeObject",
				"readObject(" + declared + ".class)",
				accessor,
				setter
			);
		}

		throw new GradleException(
			"cannot generate a serialization codec for " +
				owner.getName() +
				"." +
				name +
				": nothing writes a " +
				fieldType.getName() +
				". Supported are the primitives, their boxes, String, arrays of primitives and of" +
				" String, and another type annotated @SerialType. A class library collection is not," +
				" because its format is its own private writeObject rather than its fields."
		);
	}

	private static boolean supportedArray(Class<?> type) {
		if (!type.isArray()) return false;
		Class<?> component = type.getComponentType();
		return component.isPrimitive() || component == String.class;
	}

	private static String capitalise(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static String getter(Class<?> type, Field field) {
		String capitalised = capitalise(field.getName());
		for (String candidate : List.of("get" + capitalised, "is" + capitalised, field.getName())) {
			try {
				type.getMethod(candidate);
				return candidate + "()";
			} catch (NoSuchMethodException keepLooking) {
				// try the next shape
			}
		}
		throw new GradleException(
			type.getName() +
				"." +
				field.getName() +
				" is not public and has no getter, so a codec cannot read it"
		);
	}

	private static String setter(Class<?> type, Field field) {
		String capitalised = capitalise(field.getName());
		try {
			type.getMethod("set" + capitalised, field.getType());
			return "set" + capitalised;
		} catch (NoSuchMethodException none) {
			throw new GradleException(
				type.getName() +
					"." +
					field.getName() +
					" is not public and has no setter, so a codec cannot write it"
			);
		}
	}

	// #endregion

	/** One class in the hierarchy. */
	private record Level(
		Class<?> type,
		long identifier,
		int flags,
		List<Member> members,
		List<Member> declarationOrder
	) {}

	/** One field, with everything the generated code needs to read and write it. */
	private record Member(
		String name,
		char typeCode,
		String signature,
		String declared,
		String writer,
		String reader,
		String accessor,
		String setter
	) {
		boolean primitive() {
			return typeCode != 'L' && typeCode != '[';
		}

		/** A public field is assigned; anything else goes through the setter that reaches it. */
		String assign(String value) {
			return setter == null ? name + " = " + value : setter + "(" + value + ")";
		}

		String local() {
			return name + "Value";
		}
	}
}
