package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;

/**
 * A Workflow class written in Java.
 *
 * <p>Read the way {@link DurableObjects} reads a Durable Object: the class is loaded without being
 * initialised, so no user code runs, and it has to implement the interface the generated export
 * calls.
 *
 * @param className the fully qualified Java class name
 * @param singleton whether it is reached through a static {@code INSTANCE}, as a Kotlin object is
 * @since 1.0.3
 */
public record WorkflowClasses(String className, boolean singleton) implements Serializable {
	private static final String ENTRYPOINT = "dev.gmitch215.bytebox.workflow.WorkflowEntrypoint";

	/** {@return the class's simple name, which is what the JavaScript class is called} */
	public String simpleName() {
		int dot = className.lastIndexOf('.');
		return dot < 0 ? className : className.substring(dot + 1);
	}

	/**
	 * {@return the binding name, which is {@code WORKFLOW_} and the class name upper-snaked}
	 *
	 * <p>Derived from the same name the plugin compares against the bindings a project declared, so a
	 * project that declares its own binding for the class is not given a second one.
	 */
	public String bindingName() {
		return "WORKFLOW_" + Bindings.upperSnake(simpleName());
	}

	/** {@return the Workflow's name, the class name in kebab case, which Cloudflare keeps unique per account} */
	public String workflowName() {
		return Bindings.kebab(simpleName());
	}

	/** {@return the prefix the generated export carries} */
	public String exportPrefix() {
		return "workflow" + simpleName();
	}

	/**
	 * Reads each named class.
	 *
	 * @param classNames the fully qualified class names
	 * @param classpath where to find them and the interface they implement
	 * @return one entry per class, in the order named
	 */
	public static List<WorkflowClasses> of(List<String> classNames, Iterable<File> classpath) {
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
				WorkflowClasses.class.getClassLoader()
			)
		) {
			List<WorkflowClasses> found = new ArrayList<>();
			for (String className : classNames) {
				Class<?> type = Class.forName(className, false, loader);
				boolean implemented = false;
				for (Class<?> c = type; c != null && !implemented; c = c.getSuperclass()) {
					implemented = implementsEntrypoint(c);
				}
				if (!implemented) {
					throw new GradleException(
						className +
							" is named in workflows but does not implement WorkflowEntrypoint, so" +
							" there would be nothing for the generated class to call"
					);
				}
				found.add(new WorkflowClasses(className, Triggers.isSingleton(type)));
			}
			return found;
		} catch (ClassNotFoundException missing) {
			throw new GradleException(
				"could not find a Workflow class named in workflows; check the name against the" +
					" compiled sources",
				missing
			);
		} catch (IOException closing) {
			throw new GradleException("could not read the compiled classes", closing);
		}
	}

	private static boolean implementsEntrypoint(Class<?> type) {
		for (Class<?> implemented : type.getInterfaces()) {
			if (implemented.getName().equals(ENTRYPOINT) || implementsEntrypoint(implemented)) {
				return true;
			}
		}
		return false;
	}
}
