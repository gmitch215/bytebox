package dev.gmitch215.bytebox.durable;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.List;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * A Durable Object's SQLite database.
 *
 * <p>Reached through {@link DurableState#sql}. Two things make this different from
 * {@link dev.gmitch215.bytebox.binding.D1Database}, and both matter: a query here runs synchronously
 * rather than over a promise, and the object's storage has real transactions.
 *
 * {@snippet lang = "java":
 * state.sql("create table if not exists visits (day text primary key, count integer)");
 * state.sql("insert into visits values (?1, 1) on conflict(day) do update set count = count + 1", day);
 * long count = state.sql("select count from visits where day = ?1", day).one().get("count").asLong();
 *}
 *
 * <p>The database is private to one Durable Object instance and lives with it. A query counts against
 * that instance's storage rather than an account-level database.
 *
 * @since 1.0.0
 */
public interface SQL extends JSObject {
	/** {@return every row, read at once} */
	default List<TSObject> rows() {
		TSObject all = asArray();
		return all == null ? List.of() : all.asList();
	}

	/**
	 * {@return the single row this query answered}
	 *
	 * <p>Throws if the query answered no rows or more than one, which is what makes it worth using
	 * over reading the first of a list.
	 */
	@org.teavm.jso.JSMethod("one")
	TSObject one();

	/** {@return how many rows the query read, which is what the platform bills} */
	@JSProperty("rowsRead")
	int rowsRead();

	/** {@return how many rows the query wrote} */
	@JSProperty("rowsWritten")
	int rowsWritten();

	/** {@return the column names, in the order the query returns them} */
	default List<String> columns() {
		TSObject names = columnNames();
		return names == null ? List.of() : names.asStringList();
	}

	@org.teavm.jso.JSMethod("toArray")
	TSObject asArray();

	@JSProperty("columnNames")
	TSObject columnNames();
}
