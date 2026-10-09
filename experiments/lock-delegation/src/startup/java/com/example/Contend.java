package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/** The shape a sample has: a lock, a join and a contended lock before the first request. */
public class Contend implements Worker {

	private static final ReentrantLock LOCK = new ReentrantLock();
	private static final String GREETING;

	static {
		Thread holder = new Thread(() -> {
			LOCK.lock();
			try {
				System.out.println("holder has the lock");
				Thread.sleep(20);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			} finally {
				LOCK.unlock();
				System.out.println("holder released");
			}
		});
		holder.start();
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		LOCK.lock();
		try {
			GREETING = CompletableFuture.supplyAsync(() -> "hello from a fiber").join();
			System.out.println("clinit: " + GREETING);
		} finally {
			LOCK.unlock();
		}
	}

	private final int answer;

	public Contend() {
		answer = CompletableFuture.supplyAsync(() -> 42).join();
		System.out.println("constructed: " + answer);
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		LOCK.lock();
		try {
			return Bytebox.response(GREETING + " " + answer);
		} finally {
			LOCK.unlock();
		}
	}
}
