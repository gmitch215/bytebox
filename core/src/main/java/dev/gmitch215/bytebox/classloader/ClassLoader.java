package dev.gmitch215.bytebox.classloader;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/**
 * A class loader over an empty classpath: every lookup finds nothing.
 *
 * <p>Libraries that discover a provider by scanning for resources ask {@code getResources} first. A
 * compiled program has no classpath to enumerate, so the honest answer is an empty one, which
 * those libraries already handle as "nothing configured".
 *
 * @since 1.0.3
 */
public class ClassLoader {

	private static final ClassLoader SYSTEM = new ClassLoader();

	protected ClassLoader() {}

	protected ClassLoader(ClassLoader parent) {}

	public ClassLoader getParent() {
		return null;
	}

	public static ClassLoader getSystemClassLoader() {
		return SYSTEM;
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		throw new ClassNotFoundException(name);
	}

	public URL getResource(String name) {
		return null;
	}

	public Enumeration<URL> getResources(String name) {
		return Collections.emptyEnumeration();
	}

	public InputStream getResourceAsStream(String name) {
		return null;
	}

	public static URL getSystemResource(String name) {
		return null;
	}

	public static Enumeration<URL> getSystemResources(String name) {
		return Collections.emptyEnumeration();
	}

	public static InputStream getSystemResourceAsStream(String name) {
		return null;
	}
}
