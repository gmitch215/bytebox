package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

/**
 * One secret from a Secrets Store. Declared with {@code secrets()}, named {@code SECRETS} by
 * default.
 *
 * <p>Unlike a plain secret, which is a string on {@code env} readable at module scope, this is
 * fetched. So it blocks, and it cannot be read during module evaluation.
 *
 * @since 1.0.0
 */
public interface SecretsStore extends JSObject {
	/** {@return the secret's value} */
	default String value() {
		return Async.await(read()).stringValue();
	}

	@JSMethod("get")
	JSPromise<JSString> read();
}
