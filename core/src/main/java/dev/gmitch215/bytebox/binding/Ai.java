package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * The Workers AI binding. Declared with {@code ai()}, named {@code AI}.
 *
 * <p>Inference runs on Cloudflare's GPUs rather than in the isolate, so this always crosses the
 * network and always blocks. There is no local emulation: a test either fakes this binding or points
 * it at the real service.
 *
 * {@snippet lang = "java":
 * TSObject input = TSObject.object();
 * input.set("prompt", TSObject.of("why is the sky blue?"));
 * String answer = env.ai().run("@cf/meta/llama-3.1-8b-instruct", input).get("response").asString();
 *}
 *
 * @since 1.0.0
 */
public interface Ai extends JSObject {
	/**
	 * Runs a model.
	 *
	 * @param model the model name, such as {@code @cf/meta/llama-3.1-8b-instruct}
	 * @param input the model's input, whose shape depends on the model
	 * @return the model's output
	 */
	default TSObject run(String model, TSObject input) {
		return Async.await(runModel(model, input));
	}

	/**
	 * Runs a model with options such as {@code gateway} or {@code returnRawResponse}.
	 *
	 * @param model the model name
	 * @param input the model's input
	 * @param options the options
	 * @return the model's output
	 */
	default TSObject run(String model, TSObject input, TSObject options) {
		return Async.await(runModel(model, input, options));
	}

	/**
	 * Embeds text with an embedding model, which is the common case worth naming.
	 *
	 * @param model the embedding model
	 * @param text the text
	 * @return the response, whose {@code data} holds one vector per input
	 */
	default TSObject embed(String model, String text) {
		return run(model, TSObject.object(Map.of("text", List.of(text))));
	}

	@JSMethod("run")
	JSPromise<TSObject> runModel(String model, TSObject input);

	@JSMethod("run")
	JSPromise<TSObject> runModel(String model, TSObject input, TSObject options);
}
