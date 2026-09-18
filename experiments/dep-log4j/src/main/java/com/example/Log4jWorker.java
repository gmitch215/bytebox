package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4jWorker implements Worker {

	private static final Logger LOG = LogManager.getLogger(Log4jWorker.class);

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		LOG.info("handling {}", request.getUrl());
		return Bytebox.response("logged");
	}
}
