package dev.gmitch215.bytebox.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a JSON codec for this type.
 *
 * <p>Records are the straightforward case: the components are the fields and the canonical
 * constructor is the decoder. A class works too when it has a no-argument constructor and either
 * public fields or setters.
 *
 * <p>Nested types need the annotation as well. The generator refuses a field whose type has no codec
 * rather than emitting one that silently reads null, so a missing annotation is a build failure that
 * names the field.
 *
 * {@snippet lang = "java":
 * @JSONType
 * public record Settings(String host, int port, List<String> hosts) {}
 *}
 *
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JSONType {
	/**
	 * Whether to leave out a field holding null rather than writing it as {@code null}.
	 *
	 * @return whether to omit nulls
	 */
	boolean omitNulls() default false;
}
