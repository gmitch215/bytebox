package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;

/**
 * A Durable Object class written in Java, and which of its handlers exist.
 *
 * <p>Read from the class the same way a Worker's triggers are: the interfaces it implements are already
 * the answer, so the generated JavaScript class exposes a handler only where there is one to call.
 *
 * @param className the fully qualified Java class name
 * @param sockets whether it takes WebSockets
 * @param alarms whether it takes alarms
 * @since 1.0.0
 */
public record DurableObjects(
	String className,
	boolean sockets,
	boolean alarms
) implements Serializable {
	private static final String OBJECT = "dev.gmitch215.bytebox.durable.DurableObject";
	private static final String SOCKET = "dev.gmitch215.bytebox.durable.SocketObject";
	private static final String ALARM = "dev.gmitch215.bytebox.durable.AlarmObject";

	/** {@return the class's simple name, which is what the binding and the JavaScript class are called} */
	public String simpleName() {
		int dot = className.lastIndexOf('.');
		return dot < 0 ? className : className.substring(dot + 1);
	}

	/**
	 * {@return the binding name, which is {@code DO_} and the class name upper-snaked}
	 *
	 * <p>Derived by the same code the bindings block uses. The plugin declares a binding for any
	 * Durable Object that has none, deciding by this name, so a second derivation that disagreed with
	 * that one would declare the same binding twice.
	 */
	public String bindingName() {
		return "DO_" + Bindings.upperSnake(simpleName());
	}

	/**
	 * The prefix every export for this object shares.
	 *
	 * @return the prefix
	 */
	public String exportPrefix() {
		return "durable" + simpleName();
	}

	/**
	 * Reads each named class.
	 *
	 * <p>Loaded without initialising, so no user code runs.
	 *
	 * @param classNames the fully qualified class names
	 * @param classpath where to find them and the interfaces they implement
	 * @return one entry per class, in the order named
	 */
	public static List<DurableObjects> of(List<String> classNames, Iterable<File> classpath) {
		if (classNames.isEmpty()) return List.of();

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

		try (
			URLClassLoader loader = new URLClassLoader(
				urls.toArray(new URL[0]),
				DurableObjects.class.getClassLoader()
			)
		) {
			List<DurableObjects> found = new ArrayList<>();
			for (String className : classNames) {
				Class<?> type = Class.forName(className, false, loader);
				Set<String> implemented = new LinkedHashSet<>();
				collect(type, implemented);
				if (!implemented.contains(OBJECT)) {
					throw new GradleException(
						className +
							" is named in durableObjects but does not implement DurableObject, so" +
							" there would be nothing for the generated class to call"
					);
				}
				found.add(
					new DurableObjects(
						className,
						implemented.contains(SOCKET),
						implemented.contains(ALARM)
					)
				);
			}
			return found;
		} catch (ClassNotFoundException missing) {
			throw new GradleException(
				"could not find a Durable Object class named in durableObjects; check the name" +
					" against the compiled sources",
				missing
			);
		} catch (IOException closing) {
			throw new GradleException("could not read the compiled classes", closing);
		}
	}

	private static void collect(Class<?> type, Set<String> into) {
		if (type == null || type == Object.class) return;
		for (Class<?> implemented : type.getInterfaces()) {
			if (into.add(implemented.getName())) collect(implemented, into);
		}
		collect(type.getSuperclass(), into);
	}
}
