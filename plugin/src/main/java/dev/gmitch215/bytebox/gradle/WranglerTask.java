package dev.gmitch215.bytebox.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs the Wrangler CLI in the generated Worker project.
 *
 * <p>The account-touching commands work only when Wrangler is logged in, so each task says whether it
 * needs one. Nothing local does: compiling, packing, measuring and {@code workerDev} against the
 * local simulator all run with no credentials at all, which is the common case and stays silent.
 *
 * @since 1.0.0
 */
@DisableCachingByDefault(because = "runs an external command whose result is not a file")
public abstract class WranglerTask extends DefaultTask {

	private final ExecOperations exec;

	/**
	 * @param exec Gradle's process runner
	 */
	@Inject
	public WranglerTask(ExecOperations exec) {
		this.exec = exec;
	}

	/** {@return the Wrangler subcommand and its fixed arguments} */
	@Internal
	public abstract ListProperty<String> getCommand();

	/** {@return the generated Worker project, which is the working directory} */
	@Internal
	public abstract DirectoryProperty getProjectDirectory();

	/** {@return the pinned runner, or empty to search} */
	@Internal
	public abstract Property<String> getRunner();

	/** {@return whether this command needs a logged-in Wrangler} */
	@Internal
	public abstract Property<Boolean> getRequiresAccount();

	/** {@return whether this command changes or destroys something} */
	@Internal
	public abstract Property<Boolean> getDestructive();

	/** {@return extra arguments from the command line} */
	@Internal
	public abstract Property<String> getArgs();

	/**
	 * Extra arguments, split on spaces.
	 *
	 * @param args the arguments
	 */
	@Option(option = "args", description = "Extra arguments passed through to Wrangler")
	public void setArgsOption(String args) {
		getArgs().set(args);
	}

	/** {@return whether a write was confirmed} */
	@Internal
	public abstract Property<Boolean> getConfirmed();

	/**
	 * Confirms a command that writes.
	 *
	 * @param confirmed whether to go ahead
	 */
	@Option(option = "confirm", description = "Confirms a command that changes something")
	public void setConfirmedOption(boolean confirmed) {
		getConfirmed().set(confirmed);
	}

	/** Runs the command. */
	@TaskAction
	public void run() {
		if (getDestructive().getOrElse(false) && !getConfirmed().getOrElse(false)) {
			throw new GradleException(
				getName() +
					" changes a real resource, so it needs --confirm. Nothing has been done."
			);
		}

		Runner runner = Runner.resolve(getRunner().getOrNull());
		File directory = getProjectDirectory().get().getAsFile();
		if (!directory.isDirectory()) {
			throw new GradleException(
				"the Worker project has not been generated yet; run buildWorker first"
			);
		}

		List<String> args = new ArrayList<>(getCommand().get());
		String extra = getArgs().getOrElse("").trim();
		if (!extra.isEmpty()) args.addAll(List.of(extra.split("\\s+")));

		if (getRequiresAccount().getOrElse(false)) requireAccount(runner, directory);

		getLogger().lifecycle("bytebox: {} ({})", String.join(" ", args), runner.describe());
		exec.exec(spec -> {
			spec.commandLine(runner.command(args));
			spec.workingDir(directory);
		});
	}

	/**
	 * Checks the login before running a command that needs one.
	 *
	 * <p>So the failure names the fix rather than surfacing whatever Wrangler says about a missing
	 * token halfway through an operation.
	 */
	private void requireAccount(Runner runner, File directory) {
		if (System.getenv("CLOUDFLARE_API_TOKEN") != null) return;
		try {
			exec.exec(spec -> {
				spec.commandLine(runner.command(List.of("whoami")));
				spec.workingDir(directory);
				spec.setStandardOutput(java.io.OutputStream.nullOutputStream());
				spec.setErrorOutput(java.io.OutputStream.nullOutputStream());
			});
		} catch (RuntimeException notLoggedIn) {
			throw new GradleException(
				getName() +
					" needs a Cloudflare account. Run `" +
					runner.describe() +
					" login`, or set CLOUDFLARE_API_TOKEN.",
				notLoggedIn
			);
		}
	}
}
