package dev.gmitch215.bytebox.locks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("a completable future")
@Timeout(10)
class CompletableFutureTest {

	// what java.util.concurrent.CompletableFuture printed for CompletableFutureScenarios on JDK 27
	private static final String JDK = """
	completed: true v v v
	async chain: 42
	runAsync: null 1
	thenAccept and thenRun: ab
	supplier throws, join: CompletionException <- IllegalStateException(boom) true
	supplier throws, get: ExecutionException <- IllegalStateException(boom)
	completeExceptionally, join and get: CompletionException <- IllegalArgumentException(bad) | ExecutionException <- IllegalArgumentException(bad)
	exceptionally on a direct failure: saw IllegalArgumentException(bad)
	exceptionally downstream of a failure: saw CompletionException <- IllegalArgumentException(bad)
	exceptionally on a throwing supplier: saw CompletionException <- IllegalStateException(boom)
	exceptionally skipped on success: ok
	a throwing function: CompletionException <- UnsupportedOperationException(nope)
	thenCompose: n=3
	thenCompose, inner fails: CompletionException <- IllegalStateException(inner)
	thenCompose, function returns null: CompletionException <- NullPointerException
	thenCombine: ab
	thenCombine, both fail: CompletionException <- IllegalStateException(left)
	thenCombine, right fails: CompletionException <- IllegalStateException(right)
	handle: v/null null/IllegalStateException(e)
	whenComplete: v vnull null IllegalStateException(e) CompletionException <- IllegalStateException(e)
	whenComplete, action throws: CompletionException <- IllegalStateException(action) | CompletionException <- IllegalArgumentException(src)
	allOf: true 3
	allOf, empty: true
	allOf, leftmost failure: CompletionException <- IllegalStateException(b)
	anyOf: one two
	anyOf, completed later and failing: false CompletionException <- IllegalStateException(b)
	anyOf, none: false
	get with a timeout: TimeoutException now
	complete twice: true false false one
	getNow: absent here
	cancel: true true true CancellationException CancellationException true CompletionException <- CancellationException
	a fiber completes what get is waiting on: late
	callbacks on a pending future: 321
	toString states: [Not completed] [Not completed, 1 dependents] [Completed normally]
	""";

	@Test
	@DisplayName("prints what the JDK's prints for the same programs")
	void agreesWithTheJdk() {
		List<String> expected = JDK.lines().toList();
		List<String> actual = CompletableFutureScenarios.run();

		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), actual.get(i));
	}

	@Test
	@DisplayName("runs a task on the executor it was given")
	void runsOnTheExecutor() {
		Thread caller = Thread.currentThread();
		Thread[] ran = new Thread[1];
		CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
			ran[0] = Thread.currentThread();
			return "x";
		}, Runnable::run);

		assertTrue(f.isDone());
		assertEquals("x", f.join());
		assertEquals(caller, ran[0]);
	}

	@Test
	@DisplayName("runs an async task on a thread of its own")
	void runsOnAnotherThread() {
		Thread caller = Thread.currentThread();
		assertFalse(CompletableFuture.supplyAsync(() -> Thread.currentThread()).join() == caller);
	}

	@Test
	@DisplayName("times a get out with the exception the JDK declares")
	void timesOut() {
		CompletableFuture<String> f = new CompletableFuture<>();
		assertThrows(TimeoutException.class, () -> f.get(10, TimeUnit.MILLISECONDS));
		assertThrows(TimeoutException.class, () -> f.get(0, TimeUnit.SECONDS));
	}

	@Test
	@DisplayName("refuses null arguments the way the JDK does")
	void refusesNull() {
		CompletableFuture<String> f = new CompletableFuture<>();

		assertThrows(NullPointerException.class, () -> CompletableFuture.supplyAsync(null));
		assertThrows(NullPointerException.class, () -> CompletableFuture.runAsync(null));
		assertThrows(NullPointerException.class, () -> f.thenApply(null));
		assertThrows(NullPointerException.class, () -> f.completeExceptionally(null));
		assertThrows(NullPointerException.class, () -> CompletableFuture.failedFuture(null));
		assertThrows(NullPointerException.class, () ->
			CompletableFuture.allOf((CompletableFuture<?>) null)
		);
	}
}
