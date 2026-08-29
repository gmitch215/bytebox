package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("generating JSON codecs")
class GenerateCodecsTaskTest {

	private static final String FIXTURE = "dev.gmitch215.bytebox.gradle.fixture.";

	@TempDir
	Path directory;

	@Test
	@DisplayName("builds a record through its constructor, in declaration order")
	void aRecord() {
		String codec = scanned(codec("Point"));

		assertTrue(codec.contains("implements Codec<" + FIXTURE + "Point>"), codec);
		assertTrue(codec.contains("return new " + FIXTURE + "Point("), codec);
		assertTrue(codec.contains("value.get(\"x\").asInt()"), codec);
		assertTrue(codec.contains("value.get(\"label\").asString()"), codec);
		assertTrue(codec.contains("encoded.set(\"y\", TSObject.from(value.y()))"), codec);
	}

	@Test
	@DisplayName("reaches a private field through its accessors and a public one directly")
	void aBean() {
		String codec = scanned(codec("Profile"));

		assertTrue(codec.contains("decoded.setName(value.get(\"name\").asString())"), codec);
		assertTrue(codec.contains("encoded.set(\"name\", TSObject.from(value.getName()))"), codec);
		assertTrue(codec.contains("decoded.active = value.get(\"active\").asBoolean()"), codec);
		assertTrue(codec.contains("encoded.set(\"active\", TSObject.from(value.active))"), codec);
	}

	@Test
	@DisplayName("reads a list by its element type and an enum by name")
	void listsAndEnums() {
		String codec = scanned(codec("Profile"));

		assertTrue(codec.contains("value.get(\"tags\").asStringList()"), codec);
		assertTrue(codec.contains("TSObject.array(value.getTags())"), codec);
		assertTrue(codec.contains(FIXTURE + "Colour.valueOf("), codec);
		assertTrue(codec.contains("TSObject.of(value.getColour().name())"), codec);
	}

	@Test
	@DisplayName("delegates to another annotated type's codec rather than flattening it")
	void nestedTypes() {
		String codec = scanned(codec("Profile"));

		assertTrue(codec.contains("new dev_gmitch215_bytebox_gradle_fixture_PointCodec()"), codec);
	}

	@Test
	@DisplayName("finds an annotated type nested inside another class")
	void nestedDeclaration() {
		scan();

		assertTrue(
			Files.exists(codec("Nested_Inner")),
			"a nested @JSONType carries a dollar sign in its binary name and is still annotated"
		);
	}

	@Test
	@DisplayName("registers everything it wrote")
	void registry() {
		scan();
		String registry = Projects.read(generated(GenerateCodecsTask.REGISTRY));

		assertTrue(registry.contains("JSON.register(" + FIXTURE + "Point.class"), registry);
		assertTrue(registry.contains("public static void register()"), registry);
	}

	@Test
	@DisplayName("writes a registry that does nothing when no type is annotated")
	void emptyRegistry() {
		GenerateCodecsTask task = task();
		task.generate();

		assertTrue(
			Projects.read(generated(GenerateCodecsTask.REGISTRY)).contains(
				"no types were annotated"
			)
		);
	}

	@Test
	@DisplayName("refuses a field nothing converts, naming the field and the type")
	void unconvertibleField() {
		GradleException failure = refuse("Refused$OddJsonField");

		assertTrue(
			failure.getMessage().contains("nothing converts a Instant"),
			failure.getMessage()
		);
		assertTrue(failure.getMessage().contains("json(mapper)"), failure.getMessage());
	}

	@Test
	@DisplayName("refuses a list whose element type has no reader")
	void unconvertibleElement() {
		GradleException failure = refuse("Refused$OddList");

		assertTrue(failure.getMessage().contains("List<Instant>"), failure.getMessage());
	}

	@Test
	@DisplayName("refuses a type with no fields, which would encode to an empty object")
	void noFields() {
		GradleException failure = refuse("Refused$NoFields");

		assertTrue(failure.getMessage().contains("no fields to convert"), failure.getMessage());
	}

	@Test
	@DisplayName("refuses a private field with no getter")
	void noGetter() {
		GradleException failure = refuse("Refused$Shy");

		assertTrue(failure.getMessage().contains("has no getter"), failure.getMessage());
	}

	@Test
	@DisplayName("refuses a private field with no setter")
	void noSetter() {
		GradleException failure = refuse("Refused$Deaf");

		assertTrue(failure.getMessage().contains("has no setter"), failure.getMessage());
	}

	@Test
	@DisplayName("names the type that could not be found")
	void missingType() {
		GradleException failure = refuse("com.example.Absent");

		assertTrue(failure.getMessage().contains("could not find the type"), failure.getMessage());
	}

	private GenerateCodecsTask task() {
		GenerateCodecsTask task = Projects.task(
			Projects.project(directory.resolve("project")),
			GenerateCodecsTask.class
		);
		task.getClasspath().from(Projects.classpath());
		task.getOutputDirectory().set(directory.resolve("out").toFile());
		return task;
	}

	/** Walks the compiled test classes, which is what a real build points at its own output. */
	private void scan() {
		GenerateCodecsTask task = task();
		task.getScanned().from(Projects.testClasses());
		task.generate();
	}

	private String scanned(Path file) {
		scan();
		return Projects.read(file);
	}

	private GradleException refuse(String simpleName) {
		GenerateCodecsTask task = task();
		task.getTypes().set(List.of(FIXTURE + simpleName));
		return assertThrows(GradleException.class, task::generate);
	}

	private Path codec(String simpleName) {
		return generated("dev_gmitch215_bytebox_gradle_fixture_" + simpleName + "Codec");
	}

	private Path generated(String className) {
		return directory
			.resolve("out")
			.resolve(GenerateCodecsTask.PACKAGE.replace('.', '/'))
			.resolve(className + ".java");
	}
}
