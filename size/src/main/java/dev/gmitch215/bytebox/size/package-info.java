/**
 * Attributing a compiled module's bytes.
 *
 * <p>{@link dev.gmitch215.bytebox.size.ModuleSplit} reads a WebAssembly binary's code section and
 * divides it three ways: the compiler's own emitted runtime, the class library, and the program's
 * code. That split is what says whether a size problem is worth attacking in the class library, in
 * the program, or not at all — and it is the measurement a proposal to replace the compiler outright
 * has to clear first.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.size;
