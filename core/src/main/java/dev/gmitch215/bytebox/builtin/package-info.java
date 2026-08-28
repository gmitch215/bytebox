/**
 * Platform facilities exposed as themselves rather than behind a Java API.
 *
 * <p>The isolate already carries locale data, timezone rules, every text encoding the WHATWG registry
 * lists, and Web Crypto. Reaching them costs no bytes in the binary, while the class library's
 * equivalents would compile their tables into it: {@code String.format} and its locale graph measure
 * over 230 KB of WebAssembly against a hello world of 16 KB.
 *
 * <p>These are deliberately not the {@code java.text} and {@code java.time} APIs. They are the
 * JavaScript ones, named as such, for the cases where a standard-looking Java surface would be a lie
 * about which rules apply. Where a Java API can be backed faithfully it is, and that needs no
 * separate name.
 *
 * <p>{@link dev.gmitch215.bytebox.builtin.Clock} is worth reading before using any of them: Workers
 * pin the clock between I/O, which changes what timing code can do.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.builtin;
