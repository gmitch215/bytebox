package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * An AI Search binding. Declared with {@code aiSearch()}, named {@code AI_SEARCH} by default.
 *
 * <p>Retrieval over an indexed corpus, optionally with a generated answer over the top.
 *
 * @since 1.0.0
 */
public interface AiSearch extends JSObject {
	/**
	 * Retrieves matching passages without generating an answer.
	 *
	 * @param query the query
	 * @return the matches
	 */
	default TSObject search(String query) {
		return Async.await(runSearch(queryOf(query)));
	}

	/**
	 * Retrieves passages and generates an answer from them.
	 *
	 * @param query the query
	 * @return the answer and the passages behind it
	 */
	default TSObject ask(String query) {
		return Async.await(runAi(queryOf(query)));
	}

	@JSMethod("search")
	JSPromise<TSObject> runSearch(TSObject options);

	@JSMethod("aiSearch")
	JSPromise<TSObject> runAi(TSObject options);

	private static TSObject queryOf(String query) {
		TSObject options = TSObject.object();
		options.set("query", TSObject.of(query));
		return options;
	}
}
