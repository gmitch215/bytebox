package dev.gmitch215.bytebox.io;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Points {@code java.io.ObjectOutputStream} and {@code ObjectInputStream} at the ones in this package.
 *
 * <p>The class library has neither, so nothing is being replaced: they are being supplied. A project
 * compiles against the JDK's and the reference is rewritten when the program is compiled to
 * WebAssembly, which is what lets a library that serialises the ordinary way work unchanged.
 *
 * @since 1.0.0
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(
			named("java.io.ObjectOutputStream").or(named("java.io.ObjectInputStream"))
		).replacePackage("java.io", "dev.gmitch215.bytebox.io");
	}
}
