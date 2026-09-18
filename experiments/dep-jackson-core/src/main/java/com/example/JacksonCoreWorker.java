package com.example;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.io.StringWriter;

public class JacksonCoreWorker implements Worker {

	private static final JsonFactory FACTORY = new JsonFactory();

	static {
		System.out.println("start");
		try {
			StringWriter out = new StringWriter();
			try (JsonGenerator gen = FACTORY.createGenerator(out)) {
				gen.writeStartObject();
				gen.writeStringField("name", "bytebox");
				gen.writeNumberField("version", 2);
				gen.writeEndObject();
			}
			System.out.println("wrote=" + out);

			StringBuilder read = new StringBuilder();
			try (JsonParser parser = FACTORY.createParser(out.toString())) {
				while (parser.nextToken() != null) {
					if (parser.currentToken() == JsonToken.FIELD_NAME) {
						read.append(parser.currentName()).append(' ');
					}
				}
			}
			System.out.println("fields=" + read.toString().trim());
		} catch (Throwable t) {
			System.out.println("THREW " + t.getClass().getName() + ": " + t.getMessage());
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
