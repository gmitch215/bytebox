package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("reporting the bindings")
class BindingsReportTaskTest {

	@TempDir
	Path directory;

	@Test
	@DisplayName("says so when nothing is bound, rather than printing an empty table")
	void nothingDeclared() {
		assertEquals(
			List.of("bytebox: no bindings are declared"),
			BindingsReportTask.rows(List.of())
		);
	}

	@Test
	@DisplayName("names each binding, its type and what it points at")
	void table() {
		Bindings bindings = new Bindings();
		bindings.kv();
		bindings.d1("PROD", spec -> spec.setDatabaseId("abc123"));

		List<String> rows = BindingsReportTask.rows(bindings.getAll());

		assertEquals(3, rows.size(), rows.toString());
		assertTrue(rows.get(0).contains("NAME"), rows.get(0));
		assertTrue(rows.get(0).contains("POINTS AT"), rows.get(0));
		assertTrue(rows.get(1).contains("KV"), rows.get(1));
		assertTrue(rows.get(1).contains("kv"), rows.get(1));
		assertTrue(rows.get(1).contains("provisioned by Wrangler"), rows.get(1));
		assertTrue(rows.get(2).contains("PROD"), rows.get(2));
		assertTrue(rows.get(2).contains("database_id=abc123"), rows.get(2));
	}

	@Test
	@DisplayName("pads every column to the widest cell, so the table lines up")
	void aligned() {
		Bindings bindings = new Bindings();
		bindings.kv();
		bindings.versionMetadata();

		List<String> rows = BindingsReportTask.rows(bindings.getAll());

		int width = rows.get(0).length() - rows.get(0).trim().length();
		assertEquals(2, width, "the header is indented like the rows");
		assertEquals(
			rows.get(1).indexOf("kv "),
			rows.get(2).indexOf("version_metadata"),
			rows.toString()
		);
	}

	@Test
	@DisplayName("writes the report to the build log")
	void runs() {
		Bindings bindings = new Bindings();
		bindings.kv();

		BindingsReportTask task = Projects.task(
			Projects.project(directory),
			BindingsReportTask.class
		);
		task.getBindings().set(bindings.getAll());

		task.report();
	}
}
