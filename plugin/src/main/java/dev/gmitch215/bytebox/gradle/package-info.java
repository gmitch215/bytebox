/**
 * The Gradle plugin that turns a Java workspace into a Cloudflare Worker.
 *
 * <p>{@link dev.gmitch215.bytebox.gradle.ByteboxPlugin} registers the {@code bytebox { }} block and a
 * pipeline ending in {@code buildWorker}. What makes it more than a wrapper around the compiler is
 * that it derives rather than asks: which handlers the Worker exports comes from the interfaces the
 * handler class implements, and which JSON codecs exist comes from what is annotated. Neither is
 * declared twice, so neither can drift.
 *
 * <p>Two things are worth knowing before reading the tasks. The generated code lives in its own
 * source set rather than in {@code main}, because deciding what to generate means reading the
 * compiled handler and putting the result in {@code main} would make compiling it depend on itself.
 * And everything up to {@code buildWorker} runs with no Cloudflare credentials at all — only the
 * commands that reach the account need a login, and each says so.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.gradle;
