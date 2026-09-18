package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.Map;

public class JacksonWorker implements Worker {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		try {
			return Bytebox.response(MAPPER.writeValueAsString(Map.of("url", request.getUrl())));
		} catch (Exception failed) {
			return Bytebox.response("failed: " + failed.getMessage());
		}
	}
}
