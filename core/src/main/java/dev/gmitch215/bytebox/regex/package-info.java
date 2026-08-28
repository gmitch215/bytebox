/**
 * What makes {@code java.util.regex} work here, which nothing in a project has to name.
 *
 * <p>The class library has a full pattern compiler and matcher, and they measure 68 KB of WebAssembly
 * after compression to do what the isolate can already do. A project writes {@code java.util.regex} as
 * it would anywhere and the compiler points the references here.
 *
 * <p>The pattern is rewritten rather than passed through, because the two syntaxes agree about most
 * things and disagree about a handful that look identical:
 * {@link dev.gmitch215.bytebox.regex.Translate} lists them and rewrites each to something exact. What
 * has no exact equivalent is refused when the pattern is compiled, naming what it was, so a pattern
 * either means here what it means on a JVM or it does not compile.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.regex;
