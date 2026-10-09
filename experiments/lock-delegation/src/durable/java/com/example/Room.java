package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.durable.DurableObject;
import dev.gmitch215.bytebox.durable.DurableState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/** A Durable Object whose constructor takes a contended lock, sleeps and joins. */
public class Room implements DurableObject {

	private static final ReentrantLock LOCK = new ReentrantLock();
	private static int built;

	private final int number;
	private final int answer;

	public Room() {
		Thread holder = new Thread(() -> {
			LOCK.lock();
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			} finally {
				LOCK.unlock();
			}
		});
		holder.start();
		LOCK.lock();
		try {
			Thread.sleep(5);
			answer = CompletableFuture.supplyAsync(() -> 42).join();
			number = ++built;
			System.out.println("room " + number + " built");
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		} finally {
			LOCK.unlock();
		}
	}

	@Override
	public Response fetch(Request request, DurableState state, Env env) {
		return Bytebox.response("room " + number + " answer " + answer);
	}
}
