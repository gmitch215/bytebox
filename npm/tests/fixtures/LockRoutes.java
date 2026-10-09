package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** The routes that block on a lock or a future, kept apart from the rest of the fixture. */
final class LockRoutes {

	/** What the handler's constructor recorded while it blocked, read back through /startuplock. */
	static final List<String> STARTUP = new ArrayList<>();

	private LockRoutes() {}

	/** Blocks on a contended lock and a join, as a handler constructor that opens a resource would. */
	static void startup() {
		try {
			contend(STARTUP);
		} catch (InterruptedException e) {
			STARTUP.add("interrupted");
		}
	}

	/** The same contended lock and join, for a constructor that has to record that it got through. */
	static List<String> blocked() {
		List<String> events = new ArrayList<>();
		try {
			contend(events);
		} catch (InterruptedException e) {
			events.add("interrupted");
		}
		return events;
	}

	static Response route(Request request) throws Exception {
		List<String> events = new ArrayList<>();
		switch (request.path()) {
			case "/startuplock" -> events.addAll(STARTUP);
			case "/lockcontend" -> contend(events);
			case "/lockcondition" -> condition(events);
			default -> future(events);
		}
		return Bytebox.response(String.join("\n", events));
	}

	private static void contend(List<String> events) throws InterruptedException {
		ReentrantLock lock = new ReentrantLock();
		lock.lock();
		events.add("first holds");
		Thread second = new Thread(() -> {
			events.add("second asks");
			lock.lock();
			events.add("second holds");
			lock.unlock();
			events.add("second released");
		});
		second.start();
		Thread.sleep(20);
		events.add("first releases");
		lock.unlock();
		second.join();
		events.add("held " + lock.isLocked());
	}

	private static void condition(List<String> events) throws InterruptedException {
		Lock lock = new ReentrantLock();
		Condition ready = lock.newCondition();
		boolean[] flag = {false};
		Thread waiter = new Thread(() -> {
			lock.lock();
			try {
				events.add("waiter waits");
				while (!flag[0]) ready.await();
				events.add("waiter wakes");
			} catch (InterruptedException e) {
				events.add("waiter interrupted");
			} finally {
				lock.unlock();
			}
		});
		waiter.start();
		Thread.sleep(20);
		lock.lock();
		flag[0] = true;
		events.add("signaller signals");
		ready.signal();
		lock.unlock();
		waiter.join();

		lock.lock();
		try {
			events.add("timed await " + ready.await(20, TimeUnit.MILLISECONDS));
		} finally {
			lock.unlock();
		}
		Thread holder = new Thread(() -> {
			lock.lock();
			try {
				Thread.sleep(80);
			} catch (InterruptedException e) {
				events.add("holder interrupted");
			} finally {
				lock.unlock();
			}
		});
		holder.start();
		Thread.sleep(20);
		events.add("tryLock short " + lock.tryLock(10, TimeUnit.MILLISECONDS));
		boolean late = lock.tryLock(5, TimeUnit.SECONDS);
		events.add("tryLock long " + late);
		if (late) lock.unlock();
		holder.join();
	}

	private static void future(List<String> events) throws Exception {
		CompletableFuture<String> chain = CompletableFuture.supplyAsync(() -> "a")
			.thenApply(s -> s + "b")
			.thenCompose(s -> CompletableFuture.supplyAsync(() -> s + "c"))
			.thenCombine(CompletableFuture.supplyAsync(() -> "d"), (x, y) -> x + y);
		events.add("chain " + chain.get());

		CompletableFuture<String> pending = new CompletableFuture<>();
		new Thread(() -> {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				return;
			}
			pending.complete("late");
		}).start();
		events.add("pending " + pending.get());

		CompletableFuture<String> never = new CompletableFuture<>();
		try {
			never.get(20, TimeUnit.MILLISECONDS);
			events.add("never no throw");
		} catch (TimeoutException e) {
			events.add("never TimeoutException");
		}

		CompletableFuture<String> boom = CompletableFuture.supplyAsync(() -> {
			throw new IllegalStateException("boom");
		});
		try {
			boom.join();
		} catch (CompletionException e) {
			events.add("join " + e.getCause().getMessage());
		}
		try {
			boom.get();
		} catch (ExecutionException e) {
			events.add("get " + e.getCause().getClass().getSimpleName());
		}

		CompletableFuture<Integer> one = CompletableFuture.supplyAsync(() -> 1);
		CompletableFuture<Integer> two = CompletableFuture.supplyAsync(() -> 2);
		CompletableFuture.allOf(one, two).join();
		events.add("allOf " + (one.join() + two.join()));
	}
}
