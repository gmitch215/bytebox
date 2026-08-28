package dev.gmitch215.bytebox.gradle;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Compiles a Java workspace into a Cloudflare Worker.
 *
 * <p>Applying this plugin registers the {@code bytebox { }} block and the tasks that turn compiled
 * classes into a deployable Worker.
 *
 * @since 1.0.0
 */
public class ByteboxPlugin implements Plugin<Project> {

	/** The task group every task this plugin registers belongs to. */
	public static final String GROUP = "bytebox";

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply("java");
		project.getPluginManager().apply("org.teavm");

		ByteboxExtension extension = project
			.getExtensions()
			.create("bytebox", ByteboxExtension.class);
		extension.getWorkerName().convention(project.getName());
		extension.getCompatibilityDate().convention(LocalDate.now(ZoneOffset.UTC).toString());
		extension.getModuleType().convention(ByteboxExtension.ModuleType.AUTO);

		project.getTasks().register("sizeReport", SizeReportTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("Measures the compiled module on every compression axis");
		});
	}
}
