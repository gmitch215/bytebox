package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibWorker implements Worker {

	private static final String ANSWER = answer();

	static {
		System.out.println("answer=" + ANSWER);
	}

	private static String answer() {
		Logger log = LoggerFactory.getLogger("probe");
		log.info("hi");
		return "logged";
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response(ANSWER);
	}
}
