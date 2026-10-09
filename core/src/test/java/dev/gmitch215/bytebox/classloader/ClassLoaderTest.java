package dev.gmitch215.bytebox.classloader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Enumeration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the class loader substitute")
class ClassLoaderTest {

	private final ClassLoader loader = ClassLoader.getSystemClassLoader();

	@Test
	@DisplayName("finds no resources, so a provider scan sees an empty classpath")
	void noResources() throws Exception {
		Enumeration<java.net.URL> found = loader.getResources("META-INF/services/x.Y");
		assertFalse(found.hasMoreElements());
		assertFalse(
			ClassLoader.getSystemResources("log4j2.component.properties").hasMoreElements()
		);
		assertNull(loader.getResource("org/slf4j/impl/StaticLoggerBinder.class"));
		assertNull(ClassLoader.getSystemResource("x"));
		assertNull(loader.getResourceAsStream("x"));
		assertNull(ClassLoader.getSystemResourceAsStream("x"));
	}

	@Test
	@DisplayName("is one shared system loader whose parent is null")
	void systemLoader() {
		assertNotNull(loader);
		assertSame(loader, ClassLoader.getSystemClassLoader());
		assertNull(loader.getParent());
	}
}
