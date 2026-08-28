/**
 * JSON, through generated codecs rather than reflection.
 *
 * <p>Annotate a type {@link dev.gmitch215.bytebox.json.JSONType} and the Gradle plugin writes its
 * codec and registers it. {@link dev.gmitch215.bytebox.json.JSON} is then the whole API.
 *
 * <p>The reason it is not reflective: a reflective decoder needs field metadata for every type that
 * could arrive through an {@code Object}-typed field, which is the whole-program closure dead-code
 * elimination exists to prune. The binary's size would track how many types were serialisable rather
 * than how many were used. Generating a codec per named type keeps that closure to what was asked
 * for.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.json;
