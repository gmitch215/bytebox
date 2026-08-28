package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ordinary Java, on a platform that has none of it.
 *
 * <p>Nothing here is a bytebox API. It is {@code java.time}, {@code java.net.http},
 * {@code java.util.regex} and {@code String.format}, written the way they are written anywhere, and
 * the compiler points each reference at an implementation that works on this runtime. That is what
 * makes an unmodified library compile: the library does not know it is being retargeted.
 *
 * <p>What each one costs, and where each one differs from a JVM, is in the technical report. The two
 * differences worth knowing at the call site are here as comments.
 */
public class StandardLibraryWorker implements Worker {

	private static final Pattern SINCE = Pattern.compile("since=(?<date>\\d{4}-\\d{2}-\\d{2})");

	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		LocalDate since = LocalDate.of(2026, 1, 1);
		Matcher asked = SINCE.matcher(request.getUrl());
		if (asked.find()) since = LocalDate.parse(asked.group("date"));

		// the clock is pinned between I/O, so this is the time the invocation began and does not move
		Instant now = Instant.now();
		ZonedDateTime local = now.atZone(ZoneId.of("America/New_York"));
		long days = ChronoUnit.DAYS.between(since, local.toLocalDate());

		StringBuilder body = new StringBuilder();
		body.append(local.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
		// %,d groups every three digits here, which is right for the locales that group in threes
		body.append(String.format("%,d days since %s%n", days, since));
		body.append(String.format("%-12s %8.2f%%%n", "elapsed", (days * 100.0) / 365));
		body.append(upstream()).append('\n');

		return Bytebox.response(body.toString());
	}

	/** A request through the modern client, which is {@code fetch} underneath and suspends the fiber. */
	private String upstream() {
		try {
			HttpResponse<String> answer = CLIENT.send(
				HttpRequest.newBuilder(URI.create("https://example.com/"))
					.header("Accept", "text/html")
					.timeout(Duration.ofSeconds(3))
					.build(),
				HttpResponse.BodyHandlers.ofString()
			);
			return String.format(
				"upstream %d, %,d bytes",
				answer.statusCode(),
				answer.body().length()
			);
		} catch (IOException | InterruptedException failed) {
			return "upstream unreachable: " + failed.getMessage();
		}
	}
}
