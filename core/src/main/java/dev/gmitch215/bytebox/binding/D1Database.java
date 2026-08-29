package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.JSArrays;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;

/**
 * A D1 database. Declared with {@code d1()}, named {@code DB} by default.
 *
 * <p>SQLite, so the SQL dialect is SQLite's. A statement is prepared and then bound, which is the
 * only way to pass a value: string interpolation into SQL is an injection and D1 offers no escaping
 * function to make it safe.
 *
 * {@snippet lang = "java":
 * D1Database db = env.d1();
 * D1Result rows = db.prepare("select name from users where team = ?").bind("core").all();
 * for (TSObject row : rows.rows()) {
 * 	System.out.println(row.get("name").asString());
 * }
 *}
 *
 * @since 1.0.0
 */
public interface D1Database extends JSObject {
	/**
	 * Prepares a statement.
	 *
	 * @param sql the SQL, with {@code ?} for each value
	 * @return the prepared statement
	 */
	D1Statement prepare(String sql);

	/**
	 * Runs one or more statements that take no values.
	 *
	 * <p>Accepts several statements separated by semicolons, which is how a migration is applied.
	 * Values cannot be bound here; use {@link #prepare(String)} for anything carrying input.
	 *
	 * <p>Rows are not returned, whatever the statements were. D1 answers a count and a duration and
	 * nothing else, so a select run this way produces no results.
	 *
	 * @param sql the SQL
	 * @return how many statements ran, and how long they took
	 */
	default D1ExecResult exec(String sql) {
		return Async.await(execute(sql));
	}

	/**
	 * Runs several prepared statements in one round trip, inside an implicit transaction.
	 *
	 * <p>D1 has no interactive transaction. This is the whole of it: either every statement applies
	 * or none does.
	 *
	 * @param statements the statements, each already bound
	 * @return one result per statement, in order
	 */
	default List<D1Result> batch(D1Statement... statements) {
		return list(Async.await(runBatch(JSArrays.of(statements))));
	}

	@JSMethod("exec")
	JSPromise<D1ExecResult> execute(String sql);

	@JSMethod("batch")
	JSPromise<JSArrayReader<D1Result>> runBatch(JSArray<D1Statement> statements);

	/**
	 * What {@link #exec(String)} answers.
	 *
	 * @since 1.0.0
	 */
	interface D1ExecResult extends JSObject {
		/** {@return how many statements ran} */
		@JSProperty
		int getCount();

		/** {@return how long they took, in milliseconds} */
		@JSProperty
		double getDuration();
	}

	private static List<D1Result> list(JSArrayReader<D1Result> results) {
		List<D1Result> all = new ArrayList<>(results.getLength());
		for (int i = 0; i < results.getLength(); i++) all.add(results.get(i));
		return all;
	}

	/**
	 * A prepared statement.
	 *
	 * <p>{@link #bind(Object...)} returns a new statement rather than mutating this one, so a
	 * prepared statement is reusable across bindings.
	 *
	 * @since 1.0.0
	 */
	interface D1Statement extends JSObject {
		/**
		 * Binds values to this statement's placeholders.
		 *
		 * @param values the values, one per {@code ?}
		 * @return a bound statement
		 */
		default D1Statement bind(Object... values) {
			JSObject[] bound = new JSObject[values.length];
			for (int i = 0; i < values.length; i++) bound[i] = Values.toJs(values[i]);
			return Options.bind(this, JSArrays.of(bound));
		}

		/** {@return every matching row} */
		default D1Result all() {
			return Async.await(allRows());
		}

		/** {@return the first row, or {@code null} when the query matched nothing} */
		default TSObject first() {
			return Async.await(firstRow());
		}

		/**
		 * Reads one column of the first row.
		 *
		 * @param column the column name
		 * @return the value, or {@code null} when the query matched nothing
		 */
		default TSObject first(String column) {
			return Async.await(firstColumn(column));
		}

		/** {@return the outcome of a statement whose rows are not wanted} */
		default D1Result run() {
			return Async.await(runStatement());
		}

		@JSMethod("all")
		JSPromise<D1Result> allRows();

		@JSMethod("first")
		JSPromise<TSObject> firstRow();

		@JSMethod("first")
		JSPromise<TSObject> firstColumn(String column);

		@JSMethod("run")
		JSPromise<D1Result> runStatement();
	}

	/**
	 * What a statement produced.
	 *
	 * @since 1.0.0
	 */
	interface D1Result extends JSObject {
		/** {@return the rows} */
		@JSProperty
		JSArrayReader<TSObject> getResults();

		/** {@return whether the statement succeeded} */
		@JSProperty
		boolean isSuccess();

		/** {@return timing and row counts} */
		@JSProperty
		D1Meta getMeta();

		/** {@return the rows, as a Java list} */
		default List<TSObject> rows() {
			JSArrayReader<TSObject> results = getResults();
			if (results == null) return List.of();
			List<TSObject> rows = new ArrayList<>(results.getLength());
			for (int i = 0; i < results.getLength(); i++) rows.add(results.get(i));
			return rows;
		}

		/** {@return how many rows the statement changed} */
		default int changes() {
			D1Meta meta = getMeta();
			return meta == null ? 0 : meta.getChanges();
		}
	}

	/**
	 * What a statement cost.
	 *
	 * @since 1.0.0
	 */
	interface D1Meta extends JSObject {
		/** {@return how many rows changed} */
		@JSProperty
		int getChanges();

		/** {@return the rowid of the last inserted row} */
		@JSProperty("last_row_id")
		double getLastRowId();

		/** {@return how many rows the database read} */
		@JSProperty("rows_read")
		int getRowsRead();

		/** {@return how many rows the database wrote} */
		@JSProperty("rows_written")
		int getRowsWritten();

		/** {@return how long the statement took, in milliseconds} */
		@JSProperty
		double getDuration();
	}
}
