package dev.gmitch215.bytebox.coverage;

import org.jacoco.core.runtime.IExecutionDataAccessorGenerator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Points an instrumented class at {@link Probes} instead of at JaCoCo's agent runtime.
 *
 * <p>The only thing offline instrumentation needs from a runtime is a call that hands back a
 * {@code boolean[]} for a class. JaCoCo's own generator emits a call into the agent jar, which
 * cannot be compiled to WebAssembly; this emits the same call shape against a class that can.
 */
final class Probing implements IExecutionDataAccessorGenerator {

	private static final String OWNER = "dev/gmitch215/bytebox/coverage/Probes";

	@Override
	public int generateDataAccessor(
		long classId,
		String className,
		int probeCount,
		MethodVisitor out
	) {
		out.visitLdcInsn(Long.valueOf(classId));
		out.visitLdcInsn(className);
		out.visitLdcInsn(Integer.valueOf(probeCount));
		out.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, "get", "(JLjava/lang/String;I)[Z", false);
		// a long takes two slots, then the name and the count
		return 4;
	}
}
