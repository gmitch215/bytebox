package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;

/**
 * Which triggers a handler class handles.
 *
 * <p>Read from the class itself rather than declared twice. A Worker implements one interface per
 * trigger, so the interfaces it implements are already the answer, and asking the class means a
 * configuration cannot drift from the code.
 *
 * @since 1.0.0
 */
public enum Triggers {
	/** HTTP requests. */
	FETCH("dev.gmitch215.bytebox.Worker", "fetch"),
	/** Cron Triggers. */
	SCHEDULED("dev.gmitch215.bytebox.Scheduled", "scheduled"),
	/** Incoming email. */
	EMAIL("dev.gmitch215.bytebox.Mail", "email"),
	/** Queue batches. */
	QUEUE("dev.gmitch215.bytebox.Consumer", "queue"),
	/** Trace events from another Worker. */
	TAIL("dev.gmitch215.bytebox.Tail", "tail"),
	/** A Durable Object alarm. */
	ALARM("dev.gmitch215.bytebox.Alarm", "alarm");

	private final String interfaceName;
	private final String exportName;

	Triggers(String interfaceName, String exportName) {
		this.interfaceName = interfaceName;
		this.exportName = exportName;
	}

	/** {@return the interface a handler implements to handle this trigger} */
	public String interfaceName() {
		return interfaceName;
	}

	/** {@return the name the generated Worker exports for this trigger} */
	public String exportName() {
		return exportName;
	}

	/**
	 * Reads the triggers a class handles.
	 *
	 * <p>Loads the class without initialising it, so no user code runs. The alternative would be
	 * parsing the class file, which answers the same question with more that can go wrong.
	 *
	 * @param handlerClass the fully qualified class name
	 * @param classpath where to find it and the interfaces it implements
	 * @return the triggers, in declaration order
	 */
	public static List<Triggers> of(String handlerClass, Iterable<File> classpath) {
		try (URLClassLoader loader = open(classpath)) {
			Class<?> handler = Class.forName(handlerClass, false, loader);
			Set<String> implemented = new LinkedHashSet<>();
			collect(handler, implemented);

			List<Triggers> found = new ArrayList<>();
			for (Triggers trigger : values()) {
				if (implemented.contains(trigger.interfaceName())) found.add(trigger);
			}
			if (found.isEmpty()) {
				throw new GradleException(
					handlerClass +
						" implements none of the bytebox trigger interfaces, so the Worker would export" +
						" no handlers. Implement Worker for HTTP, Scheduled for a Cron Trigger, Mail," +
						" Consumer, Tail or Alarm for the rest."
				);
			}
			return found;
		} catch (ClassNotFoundException missing) {
			throw new GradleException(
				"could not find the handler class " +
					handlerClass +
					"; check bytebox.handlerClass against the compiled sources",
				missing
			);
		} catch (java.io.IOException closing) {
			throw new GradleException("could not read the compiled classes", closing);
		}
	}

	/**
	 * Whether a class is reached through a shared instance because it cannot be constructed.
	 *
	 * <p>A Kotlin {@code object} compiles to a private constructor and a public static {@code INSTANCE}
	 * of its own type, and so does a Java enum constant. A class with a public no-arg constructor is
	 * constructed, whatever else it declares, so the code written for a Java handler does not change.
	 *
	 * @param handler the loaded class
	 * @return whether the instance is {@code INSTANCE}
	 */
	public static boolean isSingleton(Class<?> handler) {
		try {
			handler.getConstructor();
			return false;
		} catch (NoSuchMethodException none) {
			// no public no-arg constructor: look for the instance
		}
		try {
			Field field = handler.getDeclaredField("INSTANCE");
			int modifiers = field.getModifiers();
			return (
				Modifier.isStatic(modifiers) &&
				Modifier.isPublic(modifiers) &&
				field.getType() == handler
			);
		} catch (NoSuchFieldException none) {
			return false;
		}
	}

	/**
	 * Loads a class without initialising it and reports whether {@link #isSingleton(Class)}.
	 *
	 * @param handlerClass the fully qualified class name
	 * @param classpath where to find it
	 * @return whether the handler is reached through {@code INSTANCE}
	 */
	public static boolean isSingleton(String handlerClass, Iterable<File> classpath) {
		try (URLClassLoader loader = open(classpath)) {
			return isSingleton(Class.forName(handlerClass, false, loader));
		} catch (ClassNotFoundException missing) {
			throw new GradleException(
				"could not find the handler class " +
					handlerClass +
					"; check bytebox.handlerClass against the compiled sources",
				missing
			);
		} catch (java.io.IOException closing) {
			throw new GradleException("could not read the compiled classes", closing);
		}
	}

	private static URLClassLoader open(Iterable<File> classpath) {
		List<URL> urls = new ArrayList<>();
		for (File entry : classpath) {
			try {
				urls.add(entry.toURI().toURL());
			} catch (MalformedURLException unreachable) {
				throw new GradleException(
					"could not read the classpath entry " + entry,
					unreachable
				);
			}
		}
		return new URLClassLoader(urls.toArray(new URL[0]), Triggers.class.getClassLoader());
	}

	/** Walks the whole hierarchy, because a handler may implement a trigger through a base class. */
	private static void collect(Class<?> type, Set<String> into) {
		if (type == null || type == Object.class) return;
		for (Class<?> implemented : type.getInterfaces()) {
			if (into.add(implemented.getName())) collect(implemented, into);
		}
		collect(type.getSuperclass(), into);
	}
}
