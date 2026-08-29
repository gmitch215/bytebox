package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * Writes a JSON codec for every type asked for, and one class that registers them all.
 *
 * <p>Generated rather than reflective, and the reason is size. A reflective decoder needs field
 * metadata for every type that could arrive through an {@code Object}-typed field, which is the
 * whole-program closure dead-code elimination exists to prune — the binary would grow with how many
 * types were serialisable rather than with how many were used. A codec per named type keeps the
 * closure to what was asked for.
 *
 * @since 1.0.0
 */
@CacheableTask
public abstract class GenerateCodecsTask extends DefaultTask {

	/** The package the generated codecs live in. */
	public static final String PACKAGE = "dev.gmitch215.bytebox.generated";

	/** The class that registers every generated codec. */
	public static final String REGISTRY = "ByteboxCodecs";

	private static final String ANNOTATION = "dev.gmitch215.bytebox.json.JSONType";

	/** Resolved once per run, from the project's classloader rather than the plugin's. */
	private Class<? extends java.lang.annotation.Annotation> marker;

	/**
	 * {@return types to generate codecs for that the scan would not find}
	 *
	 * <p>Added to whatever is discovered rather than replacing it, for a type in a dependency or one
	 * whose source the project does not control.
	 */
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
			Set<String> generated = new LinkedHashSet<>();
			for (String name : annotated(loader)) {
				Class<?> type = load(loader, name);
				String codec = codecName(type);
				if (!generated.add(codec)) continue;
				Files.writeString(directory.resolve(codec + ".java"), source(type, codec));
				registrations.add(
					"\t\tJSON.register(" + type.getCanonicalName() + ".class, new " + codec + "());"
				);
			}
			Files.writeString(directory.resolve(REGISTRY + ".java"), registry(registrations));
			if (!registrations.isEmpty()) {
				getLogger().lifecycle("bytebox: generated {} JSON codec(s)", registrations.size());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Every type to generate a codec for: the annotated ones, plus whatever was named explicitly.
	 *
	 * <p>Found by walking the compiled classes rather than by asking for a list, because the
	 * annotation is already the declaration and asking twice is how the two drift apart.
	 */
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
						// a nested class carries a dollar sign and can still be annotated; only a
						// compiler-generated one cannot
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

	private static String codecName(Class<?> type) {
		return type.getName().replace('.', '_').replace('$', '_') + "Codec";
	}

	private String source(Class<?> type, String codecName) {
		List<Member> members = members(type);
		String name = type.getCanonicalName();

		StringBuilder out = new StringBuilder();
		out.append("package ").append(PACKAGE).append(";\n\n");
		out.append("import dev.gmitch215.bytebox.js.TSObject;\n");
		out.append("import dev.gmitch215.bytebox.json.Codec;\n\n");
		out.append("/** Generated by the bytebox Gradle plugin. Edits are overwritten. */\n");
		out.append("public final class ")
			.append(codecName)
			.append(" implements Codec<")
			.append(name)
			.append("> {\n\n");

		out.append("\t@Override\n\tpublic ").append(name).append(" decode(TSObject value) {\n");
		if (type.isRecord()) {
			out.append("\t\treturn new ").append(name).append("(\n");
			for (int i = 0; i < members.size(); i++) {
				out.append("\t\t\t").append(read(members.get(i)));
				out.append(i == members.size() - 1 ? "\n" : ",\n");
			}
			out.append("\t\t);\n");
		} else {
			out.append("\t\t").append(name).append(" decoded = new ").append(name).append("();\n");
			for (Member member : members) {
				out.append("\t\tdecoded.")
					.append(member.assign(read(member)))
					.append(";\n");
			}
			out.append("\t\treturn decoded;\n");
		}
		out.append("\t}\n\n");

		out.append("\t@Override\n\tpublic TSObject encode(").append(name).append(" value) {\n");
		out.append("\t\tTSObject encoded = TSObject.object();\n");
		for (Member member : members) {
			out.append("\t\tencoded.set(\"")
				.append(member.name())
				.append("\", ")
				.append(write(member))
				.append(");\n");
		}
		out.append("\t\treturn encoded;\n\t}\n}\n");
		return out.toString();
	}

	private String registry(List<String> registrations) {
		StringBuilder out = new StringBuilder();
		out.append("package ").append(PACKAGE).append(";\n\n");
		out.append("import dev.gmitch215.bytebox.json.JSON;\n\n");
		out.append("/** Generated by the bytebox Gradle plugin. Edits are overwritten. */\n");
		out.append("public final class ").append(REGISTRY).append(" {\n\n");
		out.append("\tprivate ").append(REGISTRY).append("() {}\n\n");
		out.append("\t/** Registers every generated codec. Called during module evaluation. */\n");
		out.append("\tpublic static void register() {\n");
		if (registrations.isEmpty()) {
			out.append("\t\t// no types were annotated @JSONType\n");
		} else {
			out.append(String.join("\n", registrations)).append('\n');
		}
		out.append("\t}\n}\n");
		return out.toString();
	}

	/** Reads one member out of the JavaScript value, by its declared type. */
	private String read(Member member) {
		String field = "value.get(\"" + member.name() + "\")";
		Class<?> type = member.type();
		if (type == String.class) return field + ".asString()";
		if (type == int.class || type == Integer.class) return field + ".asInt()";
		if (type == long.class || type == Long.class) return field + ".asLong()";
		if (type == double.class || type == Double.class) return field + ".asDouble()";
		if (type == float.class || type == Float.class) return field + ".asFloat()";
		if (type == short.class || type == Short.class) return field + ".asShort()";
		if (type == byte.class || type == Byte.class) return field + ".asByte()";
		if (type == char.class || type == Character.class) return field + ".asChar()";
		if (type == boolean.class || type == Boolean.class) return field + ".asBoolean()";
		if (type == List.class) return listRead(member, field);
		if (type.isEnum()) {
			return type.getCanonicalName() + ".valueOf(" + field + ".asString())";
		}
		if (type.isAnnotationPresent(marker)) {
			return "new " + codecName(type) + "().decode(" + field + ")";
		}
		throw refuse(member);
	}

	private String listRead(Member member, String field) {
		Class<?> element = member.elementType();
		if (element == String.class) return field + ".asStringList()";
		if (element == Integer.class) return field + ".asIntList()";
		if (element == Long.class) return field + ".asLongList()";
		if (element == Double.class) return field + ".asDoubleList()";
		throw refuse(member);
	}

	/** Writes one member into the JavaScript value. */
	private String write(Member member) {
		String read = "value." + member.accessor();
		Class<?> type = member.type();
		if (type == List.class) return "TSObject.array(" + read + ")";
		if (type.isEnum()) return "TSObject.of(" + read + ".name())";
		if (type.isAnnotationPresent(marker)) {
			return "new " + codecName(type) + "().encode(" + read + ")";
		}
		return "TSObject.from(" + read + ")";
	}

	private GradleException refuse(Member member) {
		return new GradleException(
			"cannot generate a JSON codec for " +
				member.owner() +
				"." +
				member.name() +
				": nothing converts a " +
				member.describe() +
				". Annotate that type @JSONType too, or read the body with json(mapper) instead."
		);
	}

	/**
	 * Loads the marker annotation from the project rather than from the plugin.
	 *
	 * <p>The plugin does not depend on bytebox-core; the project being built does. Loading it from
	 * the plugin's own classloader is what failed here, and the two loaders have to agree on the
	 * annotation's identity for {@code isAnnotationPresent} to answer true.
	 */
	@SuppressWarnings("unchecked")
	private Class<? extends java.lang.annotation.Annotation> annotation(URLClassLoader loader) {
		try {
			return (Class<? extends java.lang.annotation.Annotation>) Class.forName(
				ANNOTATION,
				false,
				loader
			);
		} catch (ClassNotFoundException missing) {
			throw new GradleException(
				"bytebox-core is not on the project's compile classpath, so @JSONType cannot be" +
					" resolved. Add a dependency on dev.gmitch215:bytebox-core.",
				missing
			);
		}
	}

	private List<Member> members(Class<?> type) {
		List<Member> members = new ArrayList<>();
		if (type.isRecord()) {
			for (RecordComponent component : type.getRecordComponents()) {
				members.add(
					new Member(
						type.getName(),
						component.getName(),
						component.getType(),
						elementOf(component.getGenericType()),
						component.getName() + "()",
						null
					)
				);
			}
			return members;
		}
		for (Field field : type.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
			boolean open = Modifier.isPublic(field.getModifiers());
			members.add(
				new Member(
					type.getName(),
					field.getName(),
					field.getType(),
					elementOf(field.getGenericType()),
					open ? field.getName() : getter(type, field),
					open ? null : setter(type, field)
				)
			);
		}
		if (members.isEmpty()) {
			throw new GradleException(
				type.getName() +
					" has no fields to convert; a JSON codec would produce an empty object"
			);
		}
		return members;
	}

	private static String getter(Class<?> type, Field field) {
		String capitalised =
			Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
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
		String capitalised =
			Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
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

	private static Class<?> elementOf(java.lang.reflect.Type generic) {
		if (!(generic instanceof java.lang.reflect.ParameterizedType parameterized)) return null;
		java.lang.reflect.Type[] arguments = parameterized.getActualTypeArguments();
		if (arguments.length != 1) return null;
		return arguments[0] instanceof Class<?> element ? element : null;
	}

	/** One field or record component a codec has to carry. */
	private record Member(
		String owner,
		String name,
		Class<?> type,
		Class<?> elementType,
		String accessor,
		String setter
	) {
		/** A public field is assigned; anything else goes through the setter that reaches it. */
		String assign(String value) {
			return setter == null ? name + " = " + value : setter + "(" + value + ")";
		}

		String describe() {
			return elementType == null
				? type.getSimpleName()
				: type.getSimpleName() + "<" + elementType.getSimpleName() + ">";
		}
	}
}
