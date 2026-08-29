package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;

/**
 * A real Gradle project, in process.
 *
 * <p>The tasks here do their work in a {@code @TaskAction} against Gradle's own property types, so
 * building one project and driving the task directly runs the same code the build does. The
 * TestKit lane proves the wiring across a real build; this one is where the generated output itself
 * gets read.
 *
 * <p>The classpath a generator reads handler classes and annotations from is this JVM's own, which is
 * what makes the fixtures in {@code fixture/} resolvable to it.
 */
final class Projects {

	private Projects() {}

	static Project project(Path directory) {
		return ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
	}

	static <T extends Task> T task(Project project, Class<T> type) {
		return project.getTasks().register(type.getSimpleName(), type).get();
	}

	/** The directory holding the compiled test classes, which is what a generator scans. */
	static File testClasses() {
		try {
			return new File(
				Projects.class.getProtectionDomain().getCodeSource().getLocation().toURI()
			);
		} catch (URISyntaxException unreachable) {
			throw new IllegalStateException("the test classes are not on a file path", unreachable);
		}
	}

	/** Everything this JVM was started with, which resolves bytebox-core and the fixtures. */
	static List<File> classpath() {
		List<File> entries = new ArrayList<>();
		for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
			entries.add(new File(entry));
		}
		return entries;
	}

	static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException unreadable) {
			throw new UncheckedIOException(unreadable);
		}
	}
}
