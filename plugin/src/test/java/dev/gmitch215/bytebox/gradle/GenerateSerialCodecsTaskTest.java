package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("generating serialization codecs")
class GenerateSerialCodecsTaskTest {

	private static final String FIXTURE = "dev.gmitch215.bytebox.gradle.fixture.";

	@TempDir
	Path directory;

	@Test
	@DisplayName("writes a record's identifier as zero, which is the rule rather than a hash")
	void aRecord() {
		String codec = scanned(codec("Ident"));

		assertTrue(codec.contains("new SerialDescriptor(\n\t\t\t\"" + FIXTURE + "Ident\""), codec);
		assertTrue(codec.contains("0L,"), codec);
		assertTrue(codec.contains("new SerialField(\"id\", 'J', null)"), codec);
		assertTrue(codec.contains("new SerialField(\"name\", 'L', \"Ljava/lang/String;\")"), codec);
		assertTrue(codec.contains("return new " + FIXTURE + "Ident("), codec);
	}

	@Test
	@DisplayName("orders fields the way the format fixes: primitives first, then by name")
	void fieldOrder() {
		String codec = scanned(codec("Ident"));

		int id = codec.indexOf("\"id\"");
		int name = codec.indexOf("\"name\"");
		assertTrue(id >= 0 && name > id, "a primitive is written before a reference\n" + codec);
	}

	@Test
	@DisplayName("carries every member shape the format can hold")
	void everyShape() {
		String codec = scanned(codec("Bag"));

		assertTrue(codec.contains("sink.writeBoolean(value.flag)"), codec);
		assertTrue(codec.contains("sink.writeByte(value.tiny)"), codec);
		assertTrue(codec.contains("sink.writeChar(value.letter)"), codec);
		assertTrue(codec.contains("sink.writeShort(value.small)"), codec);
		assertTrue(codec.contains("sink.writeInt(value.count)"), codec);
		assertTrue(codec.contains("sink.writeLong(value.big)"), codec);
		assertTrue(codec.contains("sink.writeFloat(value.ratio)"), codec);
		assertTrue(codec.contains("sink.writeDouble(value.precise)"), codec);
		assertTrue(codec.contains("sink.writeObject(value.boxed)"), codec);
		assertTrue(codec.contains("sink.writeObject(value.payload)"), codec);
		assertTrue(codec.contains("sink.writeObject(value.owner)"), codec);
		assertTrue(codec.contains("new SerialField(\"payload\", '[', \"[B\")"), codec);
		assertTrue(
			codec.contains("new SerialField(\"words\", '[', \"[Ljava/lang/String;\")"),
			codec
		);
	}

	@Test
	@DisplayName("assigns a public field and calls the setter for anything else")
	void assignsRatherThanCallingAFieldName() {
		String codec = scanned(codec("Bag"));

		assertTrue(codec.contains("value.count = source.readInt()"), codec);
		assertTrue(codec.contains("value.setHidden(source.readInt())"), codec);
		assertTrue(codec.contains("value.getHidden()"), codec);
	}

	@Test
	@DisplayName("claims the handle before reading a field, so a cycle resolves")
	void claimsItsHandleFirst() {
		String codec = scanned(codec("Bag"));

		int claim = codec.indexOf("source.claim(value)");
		int first = codec.indexOf("value.flag = ");
		assertTrue(claim >= 0 && first > claim, codec);
	}

	@Test
	@DisplayName("writes a descriptor per class in the hierarchy, most derived first")
	void hierarchy() {
		String codec = scanned(codec("Sub"));

		int sub = codec.indexOf("\"" + FIXTURE + "Sub\"");
		int base = codec.indexOf("\"" + FIXTURE + "Base\"");
		assertTrue(sub >= 0 && base > sub, "the derived descriptor comes first\n" + codec);

		int origin = codec.indexOf("value.origin = ");
		int depth = codec.indexOf("value.depth = ");
		assertTrue(
			origin >= 0 && depth > origin,
			"the base class's fields are read first\n" + codec
		);
	}

	@Test
	@DisplayName("uses a declared identifier verbatim rather than hashing the class")
	void declaredIdentifier() {
		assertTrue(scanned(codec("Bag")).contains("90210L"));
		assertTrue(scanned(codec("Sub")).contains("11L"));
	}

	@Test
	@DisplayName("writes an enum as its constant name and reads it back through a switch")
	void anEnum() {
		String codec = scanned(codec("Grade"));

		assertTrue(codec.contains("SerialDescriptor.JAVA_LANG_ENUM"), codec);
		assertTrue(codec.contains("sink.writeObject(value.name())"), codec);
		assertTrue(codec.contains("case \"PASS\" -> " + FIXTURE + "Grade.PASS;"), codec);
		assertTrue(codec.contains("throw new SerialException("), codec);
	}

	@Test
	@DisplayName("registers everything it wrote")
	void registry() {
		scan();
		String registry = Projects.read(generated(GenerateSerialCodecsTask.REGISTRY));

		assertTrue(registry.contains("Serial.register(" + FIXTURE + "Ident.class"), registry);
		assertTrue(registry.contains("Serial.register(" + FIXTURE + "Grade.class"), registry);
	}

	@Test
	@DisplayName("writes a registry that does nothing when no type is annotated")
	void emptyRegistry() {
		task().generate();

		assertTrue(
			Projects.read(generated(GenerateSerialCodecsTask.REGISTRY)).contains(
				"no types were annotated"
			)
		);
	}

	@Test
	@DisplayName("refuses a type the format would record as serializable when it is not")
	void notSerializable() {
		assertTrue(
			refuse("Refused$NotSerializable")
				.getMessage()
				.contains("does not implement Serializable")
		);
	}

	@Test
	@DisplayName("refuses Externalizable, whose format is whatever writeExternal writes")
	void externalizable() {
		assertTrue(refuse("Refused$External").getMessage().contains("is Externalizable"));
	}

	@Test
	@DisplayName("refuses a custom writeObject, which puts a class annotation in the stream")
	void customWriteObject() {
		assertTrue(refuse("Refused$Custom").getMessage().contains("declares writeObject"));
	}

	@Test
	@DisplayName("refuses a class it has no way to construct")
	void noConstructor() {
		GradleException failure = refuse("Refused$NoConstructor");

		assertTrue(
			failure.getMessage().contains("no no-argument constructor"),
			failure.getMessage()
		);
		assertTrue(failure.getMessage().contains("no Unsafe"), failure.getMessage());
	}

	@Test
	@DisplayName("refuses a collection field, whose format is its own private writeObject")
	void collectionField() {
		GradleException failure = refuse("Refused$OddSerialField");

		assertTrue(
			failure.getMessage().contains("nothing writes a java.util"),
			failure.getMessage()
		);
	}

	@Test
	@DisplayName("refuses a private field with no getter")
	void noGetter() {
		assertTrue(refuse("Refused$SerialShy").getMessage().contains("has no getter"));
	}

	@Test
	@DisplayName("refuses a private field with no setter")
	void noSetter() {
		assertTrue(refuse("Refused$SerialDeaf").getMessage().contains("has no setter"));
	}

	@Test
	@DisplayName("names the type that could not be found")
	void missingType() {
		assertTrue(refuse("com.example.Absent").getMessage().contains("could not find the type"));
	}

	private GenerateSerialCodecsTask task() {
		GenerateSerialCodecsTask task = Projects.task(
			Projects.project(directory.resolve("project")),
			GenerateSerialCodecsTask.class
		);
		task.getClasspath().from(Projects.classpath());
		task.getOutputDirectory().set(directory.resolve("out").toFile());
		return task;
	}

	private void scan() {
		GenerateSerialCodecsTask task = task();
		task.getScanned().from(Projects.testClasses());
		task.generate();
	}

	private String scanned(Path file) {
		scan();
		return Projects.read(file);
	}

	private GradleException refuse(String simpleName) {
		GenerateSerialCodecsTask task = task();
		task.getScanned().from(Projects.testClasses());
		task.getTypes().set(List.of(FIXTURE + simpleName));
		return assertThrows(GradleException.class, task::generate);
	}

	private Path codec(String simpleName) {
		return generated("dev_gmitch215_bytebox_gradle_fixture_" + simpleName + "Serial");
	}

	private Path generated(String className) {
		return directory
			.resolve("out")
			.resolve(GenerateSerialCodecsTask.PACKAGE.replace('.', '/'))
			.resolve(className + ".java");
	}
}
