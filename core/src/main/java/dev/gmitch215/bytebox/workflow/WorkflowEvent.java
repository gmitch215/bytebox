package dev.gmitch215.bytebox.workflow;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSDate;

/**
 * What started a Workflow instance, as the platform hands it to {@code run}.
 *
 * @since 1.0.3
 */
public interface WorkflowEvent extends JSObject {
	/** {@return the {@code params} the instance was created with} */
	@JSProperty("payload")
	TSObject payload();

	/** {@return the instance id} */
	@JSProperty("instanceId")
	String instanceId();

	@JSProperty("timestamp")
	JSDate getTimestamp();

	/** {@return when the instance was created, in milliseconds since the epoch} */
	default long timestamp() {
		return (long) getTimestamp().getTime();
	}
}
