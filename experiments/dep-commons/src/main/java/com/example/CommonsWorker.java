package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import org.apache.commons.lang3.StringUtils;

public class CommonsWorker implements Worker {

	static {
		System.out.println("start");
		step("reverse", () -> StringUtils.reverse("abc"));
		step("substringAfterLast", () -> StringUtils.substringAfterLast("a/b/c", "/"));
		step("isNumeric", () -> String.valueOf(StringUtils.isNumeric("123")));
		step("join", () -> StringUtils.join(new String[] { "a", "b", "c" }, ","));
		step("repeat", () -> StringUtils.repeat("ab", 3));
		step("leftPad", () -> StringUtils.leftPad("7", 3, '0'));
		step("capitalize#1", () -> StringUtils.capitalize("abc"));
		step("capitalize#2", () -> StringUtils.capitalize("def"));
		step("reverse#2", () -> StringUtils.reverse("xyz"));
		System.out.println("done");
	}

	private static void step(String name, java.util.function.Supplier<String> call) {
		try {
			System.out.println(name + "=" + call.get());
		} catch (Throwable t) {
			System.out.println(name + " THREW " + t.getClass().getName() + ": " + t.getMessage());
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
