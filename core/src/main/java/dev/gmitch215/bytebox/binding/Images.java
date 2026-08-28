package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * The Images binding. Declared with {@code images()}, named {@code IMAGES}.
 *
 * <p>Only part of this is simulated locally, so a test covering a transform beyond the simulated
 * subset has to run against the real service.
 *
 * @since 1.0.0
 */
public interface Images extends JSObject {
	/**
	 * Reads an image's format and dimensions without transforming it.
	 *
	 * @param stream the image bytes, as a stream
	 * @return the format, width and height
	 */
	default TSObject info(JSObject stream) {
		return Async.await(readInfo(stream));
	}

	/**
	 * Starts a transformation.
	 *
	 * @param stream the image bytes, as a stream
	 * @return the transformer, which is chained and then output
	 */
	Transformer input(JSObject stream);

	@JSMethod("info")
	JSPromise<TSObject> readInfo(JSObject stream);

	/**
	 * A transformation under construction.
	 *
	 * @since 1.0.0
	 */
	interface Transformer extends JSObject {
		/**
		 * Adds a transformation step.
		 *
		 * @param options {@code width}, {@code height}, {@code fit}, {@code rotate} and the rest
		 * @return this transformer
		 */
		Transformer transform(TSObject options);

		/**
		 * Renders the result.
		 *
		 * @param options {@code format}, {@code quality}
		 * @return the result, whose {@code image()} is the output stream
		 */
		default TSObject output(TSObject options) {
			return Async.await(render(options));
		}

		@JSMethod("output")
		JSPromise<TSObject> render(TSObject options);
	}
}
