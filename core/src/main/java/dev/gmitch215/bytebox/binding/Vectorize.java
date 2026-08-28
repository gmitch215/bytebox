package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSPromise;

/**
 * A Vectorize index. Declared with {@code vectorize()}, named {@code VECTORIZE} by default.
 *
 * <p>No local emulation exists, so a test either fakes this binding or points it at the real index.
 *
 * @since 1.0.0
 */
public interface Vectorize extends JSObject {
	/**
	 * Finds the nearest vectors.
	 *
	 * @param vector the query vector
	 * @param topK how many matches to return
	 * @return the matches, ordered nearest first
	 */
	default TSObject query(double[] vector, int topK) {
		TSObject options = TSObject.object();
		options.set("topK", TSObject.of(topK));
		return Async.await(runQuery(numbers(vector), options));
	}

	/**
	 * Finds the nearest vectors, returning their metadata and values too.
	 *
	 * @param vector the query vector
	 * @param topK how many matches to return
	 * @param options {@code returnValues}, {@code returnMetadata}, {@code filter}, {@code namespace}
	 * @return the matches
	 */
	default TSObject query(double[] vector, int topK, TSObject options) {
		options.set("topK", TSObject.of(topK));
		return Async.await(runQuery(numbers(vector), options));
	}

	/**
	 * Adds vectors, failing on an id that already exists.
	 *
	 * @param vectors each carrying {@code id}, {@code values} and optionally {@code metadata}
	 * @return the write's outcome
	 */
	default TSObject insert(TSObject... vectors) {
		return Async.await(insertVectors(JSArray.of(vectors)));
	}

	/**
	 * Adds or replaces vectors.
	 *
	 * @param vectors each carrying {@code id}, {@code values} and optionally {@code metadata}
	 * @return the write's outcome
	 */
	default TSObject upsert(TSObject... vectors) {
		return Async.await(upsertVectors(JSArray.of(vectors)));
	}

	/**
	 * Reads vectors by id.
	 *
	 * @param ids the ids
	 * @return the vectors that exist
	 */
	default TSObject getByIds(String... ids) {
		return Async.await(fetchByIds(strings(ids)));
	}

	/**
	 * Removes vectors by id.
	 *
	 * @param ids the ids
	 * @return the deletion's outcome
	 */
	default TSObject deleteByIds(String... ids) {
		return Async.await(removeByIds(strings(ids)));
	}

	/** {@return the index's dimension, metric and vector count} */
	default TSObject describe() {
		return Async.await(describeIndex());
	}

	@JSMethod("query")
	JSPromise<TSObject> runQuery(JSArray<TSObject> vector, TSObject options);

	@JSMethod("insert")
	JSPromise<TSObject> insertVectors(JSArray<TSObject> vectors);

	@JSMethod("upsert")
	JSPromise<TSObject> upsertVectors(JSArray<TSObject> vectors);

	@JSMethod("getByIds")
	JSPromise<TSObject> fetchByIds(JSArray<TSObject> ids);

	@JSMethod("deleteByIds")
	JSPromise<TSObject> removeByIds(JSArray<TSObject> ids);

	@JSMethod("describe")
	JSPromise<TSObject> describeIndex();

	private static JSArray<TSObject> numbers(double[] values) {
		TSObject[] wrapped = new TSObject[values.length];
		for (int i = 0; i < values.length; i++) wrapped[i] = TSObject.of(values[i]);
		return JSArray.of(wrapped);
	}

	private static JSArray<TSObject> strings(String... values) {
		TSObject[] wrapped = new TSObject[values.length];
		for (int i = 0; i < values.length; i++) wrapped[i] = TSObject.of(values[i]);
		return JSArray.of(wrapped);
	}
}
