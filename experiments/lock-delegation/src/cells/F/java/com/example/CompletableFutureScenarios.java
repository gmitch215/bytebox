package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Deterministic single-result programs over the future, one printed line each. The same source runs
 * against the JDK's class and against this one, and the two transcripts have to match.
 */
final class CompletableFutureScenarios {

	private CompletableFutureScenarios() {}

	static List<String> run() {
		List<String> out = new ArrayList<>();
		scenario(out, "completed", () -> {
			CompletableFuture<String> f = CompletableFuture.completedFuture("v");
			return f.isDone() + " " + f.join() + " " + f.get() + " " + f.getNow("x");
		});
		scenario(out, "async chain", () -> {
			CompletableFuture<Integer> f = CompletableFuture.supplyAsync(() -> 20)
				.thenApply(v -> v + 1)
				.thenApply(v -> v * 2);
			return String.valueOf(f.join());
		});
		scenario(out, "runAsync", () -> {
			int[] hit = { 0 };
			CompletableFuture<Void> f = CompletableFuture.runAsync(() -> hit[0]++);
			return f.join() + " " + hit[0];
		});
		scenario(out, "thenAccept and thenRun", () -> {
			StringBuilder seen = new StringBuilder();
			CompletableFuture.completedFuture("a")
				.thenAccept(seen::append)
				.thenRun(() -> seen.append("b"))
				.join();
			return seen.toString();
		});
		scenario(out, "supplier throws, join", () -> {
			CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
				throw new IllegalStateException("boom");
			});
			try {
				f.join();
				return "no throw";
			} catch (Throwable t) {
				return d(t) + " " + f.isCompletedExceptionally();
			}
		});
		scenario(out, "supplier throws, get", () -> {
			CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
				throw new IllegalStateException("boom");
			});
			try {
				f.get();
				return "no throw";
			} catch (Throwable t) {
				return d(t);
			}
		});
		scenario(out, "completeExceptionally, join and get", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			f.completeExceptionally(new IllegalArgumentException("bad"));
			String joined;
			try {
				f.join();
				joined = "no throw";
			} catch (Throwable t) {
				joined = d(t);
			}
			String got;
			try {
				f.get();
				got = "no throw";
			} catch (Throwable t) {
				got = d(t);
			}
			return joined + " | " + got;
		});
		scenario(out, "exceptionally on a direct failure", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			f.completeExceptionally(new IllegalArgumentException("bad"));
			return f.exceptionally(t -> "saw " + d(t)).join();
		});
		scenario(out, "exceptionally downstream of a failure", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			f.completeExceptionally(new IllegalArgumentException("bad"));
			return f
				.thenApply(s -> s + "!")
				.exceptionally(t -> "saw " + d(t))
				.join();
		});
		scenario(out, "exceptionally on a throwing supplier", () -> {
			CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
				throw new IllegalStateException("boom");
			});
			return f.exceptionally(t -> "saw " + d(t)).join();
		});
		scenario(out, "exceptionally skipped on success", () -> {
			return CompletableFuture.completedFuture("ok")
				.exceptionally(t -> "no")
				.join();
		});
		scenario(out, "a throwing function", () -> {
			CompletableFuture<String> f = CompletableFuture.completedFuture("a").thenApply(s -> {
				throw new UnsupportedOperationException("nope");
			});
			return d(f.handle((v, t) -> t).join());
		});
		scenario(out, "thenCompose", () -> {
			CompletableFuture<String> f = CompletableFuture.completedFuture(3).thenCompose(n ->
				CompletableFuture.supplyAsync(() -> "n=" + n)
			);
			return f.join();
		});
		scenario(out, "thenCompose, inner fails", () -> {
			CompletableFuture<String> f = CompletableFuture.completedFuture(3).thenCompose(n ->
				CompletableFuture.<String>failedFuture(new IllegalStateException("inner"))
			);
			return d(f.handle((v, t) -> t).join());
		});
		scenario(out, "thenCompose, function returns null", () -> {
			CompletableFuture<String> f = CompletableFuture.completedFuture(3).thenCompose(
				n -> null
			);
			return d(f.handle((v, t) -> t).join());
		});
		scenario(out, "thenCombine", () -> {
			CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> "a");
			CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> "b");
			return a.thenCombine(b, (x, y) -> x + y).join();
		});
		scenario(out, "thenCombine, both fail", () -> {
			CompletableFuture<String> a = CompletableFuture.failedFuture(
				new IllegalStateException("left")
			);
			CompletableFuture<String> b = CompletableFuture.failedFuture(
				new IllegalStateException("right")
			);
			return d(
				a
					.thenCombine(b, (x, y) -> x + y)
					.handle((v, t) -> t)
					.join()
			);
		});
		scenario(out, "thenCombine, right fails", () -> {
			CompletableFuture<String> a = CompletableFuture.completedFuture("a");
			CompletableFuture<String> b = CompletableFuture.failedFuture(
				new IllegalStateException("right")
			);
			return d(
				a
					.thenCombine(b, (x, y) -> x + y)
					.handle((v, t) -> t)
					.join()
			);
		});
		scenario(out, "handle", () -> {
			String ok = CompletableFuture.completedFuture("v")
				.handle((v, t) -> v + "/" + t)
				.join();
			String bad = CompletableFuture.<String>failedFuture(new IllegalStateException("e"))
				.handle((v, t) -> v + "/" + d(t))
				.join();
			return ok + " " + bad;
		});
		scenario(out, "whenComplete", () -> {
			StringBuilder seen = new StringBuilder();
			String ok = CompletableFuture.completedFuture("v")
				.whenComplete((v, t) -> seen.append(v).append(t))
				.join();
			CompletableFuture<String> failed = CompletableFuture.<String>failedFuture(
				new IllegalStateException("e")
			).whenComplete((v, t) -> seen.append(" ").append(v).append(" ").append(d(t)));
			String bad = d(failed.handle((v, t) -> t).join());
			return ok + " " + seen + " " + bad;
		});
		scenario(out, "whenComplete, action throws", () -> {
			CompletableFuture<String> a = CompletableFuture.completedFuture("v").whenComplete(
				(v, t) -> {
					throw new IllegalStateException("action");
				}
			);
			CompletableFuture<String> b = CompletableFuture.<String>failedFuture(
				new IllegalArgumentException("src")
			).whenComplete((v, t) -> {
				throw new IllegalStateException("action");
			});
			return d(a.handle((v, t) -> t).join()) + " | " + d(b.handle((v, t) -> t).join());
		});
		scenario(out, "allOf", () -> {
			CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> 1);
			CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> 2);
			CompletableFuture<Void> all = CompletableFuture.allOf(a, b);
			all.join();
			return all.isDone() + " " + (a.join() + b.join());
		});
		scenario(out, "allOf, empty", () -> String.valueOf(CompletableFuture.allOf().isDone()));
		scenario(out, "allOf, leftmost failure", () -> {
			CompletableFuture<String> a = CompletableFuture.completedFuture("a");
			CompletableFuture<String> b = CompletableFuture.failedFuture(
				new IllegalStateException("b")
			);
			CompletableFuture<String> c = CompletableFuture.failedFuture(
				new IllegalArgumentException("c")
			);
			return d(
				CompletableFuture.allOf(a, b, c)
					.handle((v, t) -> t)
					.join()
			);
		});
		scenario(out, "anyOf", () -> {
			CompletableFuture<String> pending = new CompletableFuture<>();
			CompletableFuture<String> one = CompletableFuture.completedFuture("one");
			CompletableFuture<String> two = CompletableFuture.completedFuture("two");
			return (
				CompletableFuture.anyOf(pending, one, two).join() +
				" " +
				CompletableFuture.anyOf(pending, two, one).join()
			);
		});
		scenario(out, "anyOf, completed later and failing", () -> {
			CompletableFuture<String> a = new CompletableFuture<>();
			CompletableFuture<String> b = new CompletableFuture<>();
			CompletableFuture<Object> any = CompletableFuture.anyOf(a, b);
			boolean before = any.isDone();
			b.completeExceptionally(new IllegalStateException("b"));
			a.complete("late");
			return before + " " + d(any.handle((v, t) -> t).join());
		});
		scenario(out, "anyOf, none", () -> String.valueOf(CompletableFuture.anyOf().isDone()));
		scenario(out, "get with a timeout", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			String timedOut;
			try {
				f.get(20, TimeUnit.MILLISECONDS);
				timedOut = "no throw";
			} catch (Throwable t) {
				timedOut = d(t);
			}
			f.complete("now");
			return timedOut + " " + f.get(1, TimeUnit.SECONDS);
		});
		scenario(out, "complete twice", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			boolean first = f.complete("one");
			boolean second = f.complete("two");
			boolean third = f.completeExceptionally(new IllegalStateException());
			return first + " " + second + " " + third + " " + f.join();
		});
		scenario(out, "getNow", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			String before = f.getNow("absent");
			f.complete("here");
			return before + " " + f.getNow("absent");
		});
		scenario(out, "cancel", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			CompletableFuture<String> dependent = f.thenApply(s -> s + "!");
			boolean cancelled = f.cancel(true);
			String joined;
			try {
				f.join();
				joined = "no throw";
			} catch (CancellationException e) {
				joined = "CancellationException";
			}
			String got;
			try {
				f.get();
				got = "no throw";
			} catch (CancellationException e) {
				got = "CancellationException";
			} catch (ExecutionException | InterruptedException e) {
				got = d(e);
			}
			return (
				cancelled +
				" " +
				f.isCancelled() +
				" " +
				f.isDone() +
				" " +
				joined +
				" " +
				got +
				" " +
				f.cancel(false) +
				" " +
				d(dependent.handle((v, t) -> t).join())
			);
		});
		scenario(out, "a fiber completes what get is waiting on", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			new Thread(() -> {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					return;
				}
				f.complete("late");
			}).start();
			return f.get();
		});
		scenario(out, "callbacks on a pending future", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			StringBuilder order = new StringBuilder();
			f.thenAccept(v -> order.append("1"));
			f.thenAccept(v -> order.append("2"));
			f.thenAccept(v -> order.append("3"));
			f.complete("x");
			return order.toString();
		});
		scenario(out, "toString states", () -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			String pending = tail(f.toString());
			f.thenApply(s -> s);
			String withDependent = tail(f.toString());
			f.complete("x");
			String done = tail(f.toString());
			return pending + " " + withDependent + " " + done;
		});
		return out;
	}

	private static String tail(String s) {
		return s.substring(s.indexOf('['));
	}

	private static void scenario(List<String> out, String name, Check check) {
		String result;
		try {
			result = check.get();
		} catch (Throwable t) {
			result = "threw " + d(t);
		}
		out.add(name + ": " + result);
	}

	private static String d(Throwable t) {
		StringBuilder s = new StringBuilder();
		for (Throwable c = t; c != null; c = c.getCause()) {
			if (s.length() > 0) s.append(" <- ");
			s.append(c.getClass().getSimpleName());
			boolean wrapper = c instanceof CompletionException || c instanceof ExecutionException;
			boolean engineText = c instanceof NullPointerException;
			if (!wrapper && !engineText && c.getMessage() != null) {
				s.append('(').append(c.getMessage()).append(')');
			}
		}
		return s.toString();
	}

	@FunctionalInterface
	private interface Check {
		String get() throws Exception;
	}
}
