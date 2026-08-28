package dev.gmitch215.bytebox.io;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a codec that reads and writes this type in Java's own serialization format.
 *
 * <p>The bytes are the ones a JVM writes, so a stream produced here is readable by
 * {@code ObjectInputStream} and a stream from {@code ObjectOutputStream} is readable here. The type
 * must implement {@link java.io.Serializable}, because the format encodes that it does.
 *
 * {@snippet lang = "java":
 * @SerialType
 * public record Order(String sku, int quantity, long total) implements Serializable {}
 *
 * byte[] wire = Serial.encode(new Order("abc", 2, 9_007_199_254_740_993L));
 * Order back = Serial.decode(wire, Order.class);
 *}
 *
 * <p>Generated rather than reflective for the same reason the JSON codecs are: a reflective
 * implementation has to keep field metadata for every class that could arrive through an
 * {@code Object}-typed field, which is the whole-program closure dead-code elimination exists to
 * prune. A codec per named type keeps the closure to what was asked for.
 *
 * <p>What the generator refuses, it refuses at build time and says why:
 *
 * <ul>
 *   <li>a custom {@code writeObject}, {@code readObject}, {@code writeReplace} or {@code readResolve}
 *   <li>{@link java.io.Externalizable}
 *   <li>a class library collection as a field type, because its format is its own private
 *       {@code writeObject} rather than its fields
 *   <li>a class that is neither a record nor has a no-argument constructor, because deserialization
 *       has to build the instance and there is no {@code Unsafe} on this platform to build it without
 * </ul>
 *
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SerialType {}
