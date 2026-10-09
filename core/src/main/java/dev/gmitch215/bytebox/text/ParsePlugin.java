package dev.gmitch215.bytebox.text;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 * Points {@code Double.parseDouble} and {@code Float.parseFloat} at {@link Parse}.
 *
 * <p>Found through {@code META-INF/services}, by the compiler. A substitution renames a whole class,
 * and the class library's {@code Double} and {@code Float} cannot be renamed without losing the
 * natives the compiler gives them, so the two methods keep their class and take a new body.
 *
 * @since 1.0.3
 */
public final class ParsePlugin implements TeaVMPlugin, ClassHolderTransformer {

	@Override
	public void install(TeaVMHost host) {
		host.add(this);
	}

	@Override
	public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
		if (cls.getName().equals("java.lang.Double")) {
			redirect(cls, context, "parseDouble", double.class);
		} else if (cls.getName().equals("java.lang.Float")) {
			redirect(cls, context, "parseFloat", float.class);
		}
	}

	private static void redirect(
		ClassHolder cls,
		ClassHolderTransformerContext context,
		String name,
		Class<?> result
	) {
		MethodHolder method = cls.getMethod(new MethodDescriptor(name, String.class, result));
		ProgramEmitter emitter = ProgramEmitter.create(method, context.getHierarchy());
		emitter.invoke(Parse.class, name, result, emitter.var(1, String.class)).returnValue();
	}
}
