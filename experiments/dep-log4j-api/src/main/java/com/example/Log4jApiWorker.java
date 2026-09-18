package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4jApiWorker implements Worker {

	static {
		System.out.println("start");
		try {
			Logger log = LogManager.getLogger(Log4jApiWorker.class);
			log.info("hello from {}", "log4j-api");
			System.out.println("logged with " + log.getClass().getName());
		} catch (Throwable t) {
			System.out.println("THREW " + t.getClass().getName() + ": " + t.getMessage());
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
