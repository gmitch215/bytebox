/**
 * Hashing for code that calls {@code java.security.MessageDigest}, which the class library does not
 * carry.
 *
 * <p>{@link dev.gmitch215.bytebox.security.MessageDigest} computes MD5, SHA-1, SHA-256, SHA-384 and
 * SHA-512 in Java. The platform's own {@code SubtleCrypto.digest} would be faster but it returns a
 * promise, and a substituted class cannot suspend to wait for one.
 *
 * @since 1.0.3
 */
package dev.gmitch215.bytebox.security;
