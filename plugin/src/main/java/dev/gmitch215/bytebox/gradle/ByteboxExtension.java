package dev.gmitch215.bytebox.gradle;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code bytebox { }} block.
 *
 * {@snippet lang = "kotlin":
 * bytebox {
 * 	handlerClass = "com.example.MyWorker"
 *
 * 	size {
 * 		module = ModuleType.AUTO
 * 		budget = "250KiB"
 * 	}
 *
 * 	wrangler {
 * 		name = "my-worker"
 * 		compatibilityDate = "2026-08-22"
 * 		routes("example.com/*")
 * 		crons("*&#47;5 * * * *")
 * 	}
 *
 * 	bindings {
 * 		kv()
 * 		d1()
 * 	}
 * }
 *}
 *
 * @since 1.0.0
 */
public abstract class ByteboxExtension {

	private final SizeSpec size;
	private final WranglerSpec wrangler;
	private final Bindings bindings = new Bindings();

	/**
	 * @param objects Gradle's factory, for the nested blocks
	 */
	@Inject
	public ByteboxExtension(ObjectFactory objects) {
		size = objects.newInstance(SizeSpec.class);
		wrangler = objects.newInstance(WranglerSpec.class);
	}

	/**
	 * {@return the class implementing one or more of the trigger interfaces}
	 *
	 * <p>Which interfaces it implements decides which handlers the generated Worker exports and which
	 * trigger keys the generated Wrangler configuration carries. An unimplemented trigger produces
	 * neither.
	 */
	public abstract Property<String> getHandlerClass();

	/**
	 * {@return whether applying the plugin puts bytebox on the classpath, which it does by default}
	 *
	 * <p>Set this false for a project that resolves bytebox some other way: a composite build, a local
	 * jar, or a version catalog entry it would rather own.
	 */
	public abstract Property<Boolean> getCoreDependency();

	/**
	 * {@return the version of {@code bytebox-core} to add, defaulting to the plugin's own}
	 *
	 * <p>The two are released together, so the default is the pair that was tested. A project that
	 * pins something else is choosing to compile against a surface these generators may not write.
	 */
	public abstract Property<String> getCoreVersion();

	/**
	 * {@return which runtime runs the Wrangler CLI}
	 *
	 * <p>Resolved once, in this order: a {@code wrangler} already on {@code PATH}, then
	 * {@code bunx}, then {@code deno}, then {@code npx}. Set this to pin one.
	 */
	public abstract Property<String> getRunner();

	/**
	 * {@return the types to generate JSON codecs for, fully qualified}
	 *
	 * <p>Only needed for a type the annotation cannot reach: one in a dependency, or one whose source
	 * you do not control. Anything annotated {@code @JSONType} in this project is found on its own.
	 */
	public abstract ListProperty<String> getJSONTypes();

	/**
	 * Adds types to generate JSON codecs for.
	 *
	 * @param types the fully qualified type names
	 */
	public void jsonTypes(String... types) {
		List<String> all = new ArrayList<>(getJSONTypes().get());
		all.addAll(List.of(types));
		getJSONTypes().set(all);
	}

	/**
	 * {@return the Java classes to expose as Durable Objects, fully qualified}
	 *
	 * <p>Which handlers each one gets is read from the interfaces it implements, so an object that takes
	 * no alarm has no alarm handler in the generated JavaScript class.
	 */
	public abstract ListProperty<String> getDurableObjectClasses();

	/**
	 * Exposes Java classes as Durable Objects.
	 *
	 * <p>The build writes the JavaScript class the runtime instantiates, the binding, and the migration
	 * that creates the class. One Java instance exists per Durable Object instance, so a field on it is
	 * that object's memory.
	 *
	 * {@snippet lang = "kotlin":
	 * bytebox {
	 * 	durableObjects("com.example.Counter", "com.example.Room")
	 * }
	 *}
	 *
	 * @param classes the fully qualified class names
	 */
	public void durableObjects(String... classes) {
		List<String> all = new ArrayList<>(getDurableObjectClasses().get());
		all.addAll(List.of(classes));
		getDurableObjectClasses().set(all);
	}

	/**
	 * {@return the types to generate {@code java.io} serialization codecs for, fully qualified}
	 *
	 * <p>Only needed for a type the annotation cannot reach. Anything annotated {@code @SerialType} in
	 * this project is found on its own.
	 */
	public abstract ListProperty<String> getSerialTypes();

	/**
	 * Adds types to generate serialization codecs for.
	 *
	 * @param types the fully qualified type names
	 */
	public void serialTypes(String... types) {
		List<String> all = new ArrayList<>(getSerialTypes().get());
		all.addAll(List.of(types));
		getSerialTypes().set(all);
	}

	/**
	 * {@return the npm packages the compiled program imports, as {@code name@range}}
	 *
	 * <p>An npm package is JavaScript, so it never enters the WebAssembly. It stays on the JavaScript
	 * side and costs bundle bytes rather than module bytes.
	 */
	public abstract ListProperty<String> getNPMPackages();

	/**
	 * Declares an npm package the compiled program imports.
	 *
	 * <p>Writes it into the generated {@code package.json} and emits a static import beside the
	 * loader, which is what makes it resolvable: a package name reached only from inside the
	 * WebAssembly lives in a custom section, and no bundler can follow that.
	 *
	 * <p>Reach it from Java with {@code @JSBodyImport}:
	 *
	 * {@snippet lang = "java":
	 * @JSBody(
	 * 	params = "size",
	 * 	imports = @JSBodyImport(alias = "nanoid", fromModule = "nanoid"),
	 * 	script = "return nanoid.nanoid(size);"
	 * )
	 * private static native String id(int size);
	 *}
	 *
	 * @param name the package name
	 * @param version the version range
	 */
	public void npm(String name, String version) {
		List<String> all = new ArrayList<>(getNPMPackages().get());
		all.add(name + "@" + version);
		getNPMPackages().set(all);
	}

	/**
	 * {@return the npm packages to generate Java bindings for}
	 *
	 * <p>A subset of {@link #getNPMPackages()}, because generating needs the package installed and a
	 * JavaScript runtime on {@code PATH} while merely depending on one does not.
	 */
	public abstract ListProperty<String> getNPMBindingPackages();

	/**
	 * Asks for generated Java bindings for packages already declared with {@link #npm}.
	 *
	 * <p>{@code generateNpmBindings} reads each package's TypeScript types and writes a class of
	 * {@code @JSBody} methods onto the main source set, so the package is callable without a line of
	 * hand-written interop. A subpath export is named the way an import names it:
	 *
	 * {@snippet lang = "kotlin":
	 * bytebox {
	 * 	npm("nanoid", "^5.0.9")
	 * 	npmBindings("nanoid", "edgeport/smtp")
	 * }
	 *}
	 *
	 * <p>What a package's types cannot express becomes {@code TSObject}, and the task prints which
	 * tier each package resolved on, so a thin binding is visible rather than a surprise.
	 *
	 * @param packages the package specifiers
	 */
	public void npmBindings(String... packages) {
		List<String> all = new ArrayList<>(getNPMBindingPackages().get());
		all.addAll(List.of(packages));
		getNPMBindingPackages().set(all);
	}

	/**
	 * {@return whether the generator may import a package to read exports no static analysis can see}
	 *
	 * <p>On by default. Installing a package already runs its own install scripts, so importing one is
	 * not a new trust boundary — but it is the only tier that executes third-party code, and this is
	 * how a build says it may not.
	 */
	public abstract Property<Boolean> getNPMIntrospection();

	/** {@return how the compiled module is packed and how big it is allowed to get} */
	public SizeSpec getSize() {
		return size;
	}

	/**
	 * Configures packing and the size budget.
	 *
	 * @param action the configuration
	 */
	public void size(Action<SizeSpec> action) {
		action.execute(size);
	}

	/** {@return what goes into the generated Wrangler configuration} */
	public WranglerSpec getWrangler() {
		return wrangler;
	}

	/**
	 * Configures the generated Wrangler configuration.
	 *
	 * @param action the configuration
	 */
	public void wrangler(Action<WranglerSpec> action) {
		action.execute(wrangler);
	}

	/** {@return the declared bindings} */
	public Bindings getBindings() {
		return bindings;
	}

	/**
	 * Declares bindings.
	 *
	 * @param action the configuration
	 */
	public void bindings(Action<Bindings> action) {
		action.execute(bindings);
	}

	/**
	 * Declares bindings by type, each taking its default name.
	 *
	 * @param types the types
	 */
	public void bindings(BindingType... types) {
		bindings.add(types);
	}
}
