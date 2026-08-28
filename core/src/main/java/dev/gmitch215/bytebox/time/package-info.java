/**
 * What makes {@code java.time} work, which nothing in a project has to name.
 *
 * <p>The class library has no {@code java.time} at all, so a program using a date would fail while it
 * was being compiled. A project writes {@code java.time} as it would anywhere, and the compiler points
 * those references at an implementation of the same API; the classes here are the two pieces of that
 * implementation the platform has to supply itself, namely where the timezone rules come from.
 *
 * <p>The rules come from {@code Intl}, which the isolate already carries, rather than from a compiled
 * copy of the timezone database inside the binary. {@link dev.gmitch215.bytebox.time.IntlZoneRules}
 * derives the rest of the interface from the one question that answers, and records the three places
 * where deriving is not the same as reading.
 *
 * <p>The clock is a separate matter, and {@link dev.gmitch215.bytebox.builtin.Clock} is the thing to
 * read about it: Workers pin the clock between I/O, so {@code Instant.now()} does not advance inside a
 * request.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.time;
