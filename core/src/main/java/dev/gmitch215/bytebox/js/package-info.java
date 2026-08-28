/**
 * Reaching any JavaScript value from Java.
 *
 * <p>{@link dev.gmitch215.bytebox.js.TSObject} is the floor of the generated npm bindings: a member
 * the generator could not type, a declaration written as {@code any}, or a package with no type
 * information at all binds as one, so nothing is ever unreachable. It is also the body type of a
 * Queue message and the row type of a D1 result, both of which are arbitrary JavaScript values.
 *
 * <p>Nothing is copied. A {@code TSObject} is the JavaScript value itself and its accessors read
 * through to it.
 *
 * <p>One conversion rule is worth knowing before the rest: a Java {@code long} crosses the boundary
 * as a {@code BigInt} and every other numeric type as a {@code Number}, {@code char} included, which
 * arrives as its UTF-16 code unit. The readers accept either kind, so this matters mainly when
 * handing a value to a JavaScript API that will only take one of them.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.js;
