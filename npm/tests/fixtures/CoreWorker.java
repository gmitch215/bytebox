package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Alarm;
import dev.gmitch215.bytebox.Cron;
import dev.gmitch215.bytebox.InboundMail;
import dev.gmitch215.bytebox.Mail;
import dev.gmitch215.bytebox.MessageBatch;
import dev.gmitch215.bytebox.Message;
import dev.gmitch215.bytebox.Scheduled;
import dev.gmitch215.bytebox.Tail;
import dev.gmitch215.bytebox.TraceItem;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.binding.AnalyticsEngine;
import dev.gmitch215.bytebox.binding.D1Database;
import dev.gmitch215.bytebox.binding.DurableObjectNamespace;
import dev.gmitch215.bytebox.binding.Hyperdrive;
import dev.gmitch215.bytebox.binding.KVNamespace;
import dev.gmitch215.bytebox.binding.Queue;
import dev.gmitch215.bytebox.binding.R2Bucket;
import dev.gmitch215.bytebox.binding.Vectorize;
import dev.gmitch215.bytebox.binding.Workflow;
import dev.gmitch215.bytebox.builtin.Clock;
import dev.gmitch215.bytebox.builtin.Crypto;
import dev.gmitch215.bytebox.builtin.Intl;
import dev.gmitch215.bytebox.builtin.Text;
import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.concurrent.Future;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.json.JSON;
import dev.gmitch215.bytebox.socket.Socket;
import dev.gmitch215.bytebox.socket.Sockets;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A worker that exercises core against real bindings, one route per thing being proved. */
public class CoreWorker
	implements Worker, Scheduled, Mail, dev.gmitch215.bytebox.Consumer<TSObject>, Tail, Alarm {

	/** What each trigger recorded, read back through /fired so one route can assert them all. */
	static final StringBuilder FIRED = new StringBuilder();

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		if (request.path().equals("/throws")) return route(request, env);
		try {
			return route(request, env);
		} catch (Throwable failure) {
			return Bytebox.json(
				json(
					pair("failed", failure.getClass().getName()),
					pair("message", String.valueOf(failure.getMessage()))
				),
				500
			);
		}
	}

	private Response route(Request request, Env env) {
		return switch (request.path()) {
			case "/url" -> url(request);
			case "/vars" -> vars(env);
			case "/kv" -> kv(env);
			case "/r2" -> r2(env);
			case "/d1" -> d1(env);
			case "/async" -> async();
			case "/resolve" -> resolveOnly();
			case "/reject" -> rejectOnly();
			case "/builtin" -> builtin();
			case "/bindings" -> bindings(env);
			case "/regex" -> regex();
			case "/socket" -> socket(
				request.query("host", "127.0.0.1"),
				Integer.parseInt(request.query("port", "3025"))
			);
			case "/jdksocket" -> jdkSocket(
				request.query("host", "127.0.0.1"),
				Integer.parseInt(request.query("port", "3025"))
			);
			case "/bytes" -> bytes(env);
			case "/tls" -> tls(
				request.query("host", "127.0.0.1"),
				Integer.parseInt(request.query("port", "8443"))
			);
			case "/hyperdrive" -> unchecked(() -> hyperdrive(env));
			case "/durable" -> durable(env);
			// forwarded rather than answered here, so the upgrade completes inside the instance
			case "/durablesocket" -> env
				.durableObject("DO_COUNTER")
				.byName("global")
				.fetch(request);
			case "/durableseen" -> env
				.durableObject("DO_COUNTER")
				.byName("global")
				.fetch("https://counter/seen");
			case "/rejects" -> rejects();
			case "/fetch" -> fetch(request.query("base", "http://echo.test"));
			case "/urlconnection" -> unchecked(() ->
				urlConnection(request.query("base", "http://echo.test"))
			);
			case "/httpclient" -> unchecked(() ->
				httpClient(request.query("base", "http://echo.test"))
			);
			case "/services" -> services(env);
			case "/overloads" -> overloads(env);
			case "/futures" -> futures();
			case "/fired" -> Bytebox.response(FIRED.toString());
			case "/tsobject" -> tsobject();
			case "/numbers" -> numbers();
			case "/collections" -> collections();
			case "/json" -> json();
			case "/throws" -> thrown();
			case "/missing" -> missing(env);
			default -> Bytebox.response("no route for " + request.path(), 404);
		};
	}

	// #region the triggers other than fetch

	@Override
	public void scheduled(Cron cron, Env env, ExecutionCtx ctx) {
		FIRED.append("cron:").append(cron.expression()).append('@').append(cron.scheduledAt());
		FIRED.append('|');
	}

	@Override
	public void email(InboundMail mail, Env env, ExecutionCtx ctx) {
		FIRED.append("mail:").append(mail.from()).append("->").append(mail.to()).append('|');
		mail.forward("inbox@example.com");
		FIRED.append("disposition:").append(mail.disposition()).append('|');
	}

	@Override
	public void queue(MessageBatch<TSObject> batch, Env env, ExecutionCtx ctx) {
		FIRED.append("queue:").append(batch.queue()).append(':');
		for (Message<TSObject> message : batch.messages()) {
			FIRED.append(message.id()).append('=').append(message.body().get("n").asInt());
			FIRED.append('#').append(message.attempts());
			message.ack();
		}
		batch.ackAll();
		FIRED.append('|');
	}

	@Override
	public void tail(List<TraceItem> events, Env env, ExecutionCtx ctx) {
		FIRED.append("tail:").append(events.size());
		for (TraceItem event : events) FIRED.append(':').append(event.getOutcome());
		FIRED.append('|');
	}

	@Override
	public void alarm(Env env, ExecutionCtx ctx) {
		FIRED.append("alarm|");
	}

	// #endregion

	private Response url(Request request) {
		return Bytebox.json(
			json(
				pair("path", request.path()),
				pair("host", request.host()),
				pair("origin", request.origin()),
				pair("method", request.getMethod()),
				pair("team", String.valueOf(request.query("team"))),
				pair("absent", String.valueOf(request.query("absent"))),
				pair("fallback", request.query("absent", "none")),
				pair("accept", request.getHeaders().get("accept", "*/*"))
			)
		);
	}

	private Response vars(Env env) {
		return Bytebox.json(
			json(
				pair("greeting", env.var("GREETING")),
				pair("missing", String.valueOf(env.var("NOPE"))),
				pair("fallback", env.var("NOPE", "fell back")),
				pair("hasKv", String.valueOf(env.has("KV"))),
				pair("hasNope", String.valueOf(env.has("NOPE")))
			)
		);
	}

	private Response kv(Env env) {
		KVNamespace kv = env.kv();
		kv.put("greeting", "hello from kv");
		kv.put("counted", "1");
		String read = kv.get("greeting");
		String absent = kv.get("never-written");
		kv.delete("counted");
		String deleted = kv.get("counted");
		List<String> keys = kv.listAll("gree");
		return Bytebox.json(
			json(
				pair("read", read),
				pair("absent", String.valueOf(absent)),
				pair("deleted", String.valueOf(deleted)),
				pair("keys", String.join(",", keys))
			)
		);
	}

	private Response r2(Env env) {
		R2Bucket blob = env.r2();
		blob.put("greeting.txt", "hello from r2", "text/plain");
		String read = blob.getText("greeting.txt");
		R2Bucket.R2Object head = blob.head("greeting.txt");
		String absent = blob.getText("never-written");
		blob.delete("greeting.txt");
		return Bytebox.json(
			json(
				pair("read", read),
				pair("size", String.valueOf((int) head.getSize())),
				pair("key", head.getKey()),
				pair("absent", String.valueOf(absent)),
				pair("gone", String.valueOf(blob.getText("greeting.txt")))
			)
		);
	}

	private Response d1(Env env) {
		D1Database db = env.d1();
		db.exec("create table if not exists teams (name text primary key, size integer)");
		db.exec("delete from teams");
		db.prepare("insert into teams (name, size) values (?, ?)").bind("core", 3).run();
		db.prepare("insert into teams (name, size) values (?, ?)").bind("plugin", 1).run();
		TSObject one = db.prepare("select size from teams where name = ?").bind("core").first();
		D1Database.D1Result all = db.prepare("select name from teams order by name").all();
		List<TSObject> rows = all.rows();
		StringBuilder names = new StringBuilder();
		for (TSObject row : rows) {
			if (names.length() > 0) names.append(',');
			names.append(row.get("name").asString());
		}
		D1Database.D1Result deleted = db.prepare("delete from teams where name = ?")
			.bind("plugin")
			.run();
		return Bytebox.json(
			json(
				pair("size", String.valueOf(one.get("size").asInt())),
				pair("names", names.toString()),
				pair("rows", String.valueOf(rows.size())),
				pair("changes", String.valueOf(deleted.changes()))
			)
		);
	}

	private Response resolveOnly() {
		Future<TSObject> ok = Async.supply(() -> TSObject.of("value"));
		String value = ok.join().asString();
		return Bytebox.json(json(pair("value", value)));
	}

	private Response rejectOnly() {
		Future<TSObject> bad = Async.supply(() -> {
				throw new IllegalStateException("on purpose");
		});
		String outcome;
		try {
			bad.join();
			outcome = "joined without throwing";
		} catch (Throwable failure) {
			outcome = "caught " + failure.getClass().getSimpleName();
		}
		return Bytebox.json(json(pair("outcome", outcome)));
	}

	private Response async() {
		long before = (long) Clock.now();
		Future<TSObject> first = Async.supply(() -> {
			Async.sleep(5);
			return TSObject.of("first done");
		});
		Future<TSObject> second = Async.supply(() -> TSObject.of("second done"));
		String firstValue = first.join().asString();
		String secondValue = second.join().asString();
		Async.sleep(10);
		long after = (long) Clock.now();
		Future<TSObject> failed = Async.supply(() -> {
			throw new IllegalStateException("on purpose");
		});
		return Bytebox.json(
			json(
				pair("first", firstValue),
				pair("second", secondValue),
				pair("recovered", failed.orElse(TSObject.of("fell back")).asString()),
				pair("clockMoved", String.valueOf(after > before))
			)
		);
	}

	private Response builtin() {
		String sha = Text.toHex(Crypto.sha256("hello world!")).substring(0, 16);
		String b64 = Text.toBase64(Text.fromHex("48656c6c6f"));
		String hex = Text.toHex(Text.fromHex("cafe00"));
		String uuid = String.valueOf(Crypto.uuid().length());
		String num = Intl.number(1234.5, "en-US");
		String cur = Intl.currency(9.99, "en-US", "USD");
		String dt = Intl.dateTime(0, "en-US", "UTC");
		String tz = Intl.timeZone();
		String zs = String.valueOf(Clock.supports("Europe/London"));
		String ls = String.valueOf(Intl.supports("fr-FR"));
		String iso = Clock.iso(0);
		return Bytebox.json(
			json(
				pair("sha256", sha),
				pair("base64", b64),
				pair("hexRoundTrip", hex),
				pair("uuidLength", uuid),
				pair("number", num),
				pair("currency", cur),
				pair("dateTime", dt),
				pair("timeZone", tz),
				pair("zoneSupported", zs),
				pair("localeSupported", ls),
				pair("iso", iso)
			)
		);
	}

	private Response tsobject() {
		TSObject object = TSObject.object();
		object.set("name", TSObject.of("bytebox"));
		object.set("port", TSObject.of(8787));
		object.set("ready", TSObject.of(true));
		TSObject parsed = TSObject.fromJson("{\"a\":[1,2,3],\"b\":{\"c\":\"d\"}}");
		return Bytebox.json(
			json(
				pair("name", object.get("name").asString()),
				pair("port", String.valueOf(object.get("port").asInt())),
				pair("ready", String.valueOf(object.get("ready").asBoolean())),
				pair("keys", String.join(",", object.keys())),
				pair("typeOf", object.get("name").typeOf()),
				pair("arrayLength", String.valueOf(parsed.get("a").length())),
				pair("nested", parsed.get("b").get("c").asString()),
				pair("isArray", String.valueOf(parsed.get("a").isArray())),
				pair("missingIsNull", String.valueOf(parsed.get("zzz").isNull())),
				pair("json", object.toJson())
			)
		);
	}

	private Response numbers() {
		TSObject big = TSObject.of(9007199254740993L);
		TSObject small = TSObject.of(42);
		TSObject parsed = TSObject.fromJson("{\"n\":70000,\"f\":1.5,\"s\":\"7\"}");
		return Bytebox.json(
			json(
				pair("longType", big.typeOf()),
				pair("longExact", String.valueOf(big.asLong())),
				pair("longRoundTrip", String.valueOf(TSObject.of(Long.MIN_VALUE).asLong())),
				pair("intType", small.typeOf()),
				pair("numberAsLong", String.valueOf(small.asLong())),
				pair("longAsInt", String.valueOf(big.asInt())),
				pair("shortWraps", String.valueOf(parsed.get("n").asShort())),
				pair("byteWraps", String.valueOf(parsed.get("n").asByte())),
				pair("floatKeeps", String.valueOf(parsed.get("f").asFloat())),
				pair("charOfString", String.valueOf(parsed.get("s").asChar())),
				pair("charOfNumber", String.valueOf(TSObject.of('A').asChar())),
				pair("charIsString", TSObject.of('A').typeOf()),
				pair("saturatesUp", String.valueOf(TSObject.of(1e300).asLong())),
				pair("saturatesDown", String.valueOf(TSObject.of(-1e300).asLong())),
				pair("nanIsZero", String.valueOf(TSObject.of(Double.NaN).asLong())),
				pair("isBigInt", String.valueOf(big.isBigInt())),
				pair("isNumber", String.valueOf(small.isNumber()))
			)
		);
	}

	private Response collections() {
		TSObject array = TSObject.array(List.of("a", "b", "c"));
		TSObject set = TSObject.set(List.of(1, 2, 2, 3));
		TSObject map = TSObject.map(Map.of("only", "value"));
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("nested", List.of(1, 2));
		TSObject object = TSObject.object(source);
		TSObject built = TSObject.array();
		built.push(TSObject.of("pushed"));
		return Bytebox.json(
			json(
				pair("array", String.join("|", array.asStringList())),
				pair("arrayLength", String.valueOf(array.length())),
				pair("streamed", array.stream().map(TSObject::asString).sorted().reduce("", String::concat)),
				pair("setIsSet", String.valueOf(set.isSet())),
				pair("setSize", String.valueOf(set.length())),
				pair("setContents", String.valueOf(set.asIntList())),
				pair("mapIsMap", String.valueOf(map.isMap())),
				pair("mapKeys", String.join(",", map.keys())),
				pair("mapValue", map.asMap().get("only").asString()),
				pair("objectNested", String.valueOf(object.get("nested").asIntList())),
				pair("objectIsMap", String.valueOf(object.isMap())),
				pair("pushed", String.join("|", built.asStringList())),
				pair("asSet", String.valueOf(array.asSet().size()))
			)
		);
	}

	private Response json() {
		// the long arrives as a string, which is the form the encoder writes and the only form that
		// survives JSON.parse above 2^53
		String source =
			"{\"sku\":\"A-1\",\"quantity\":3,\"total\":\"9007199254740993\"," +
			"\"weight\":1.5,\"paid\":true,\"status\":\"SHIPPED\",\"tags\":[\"a\",\"b\"]}";
		Order decoded = JSON.parse(source, Order.class);
		String encoded = JSON.stringify(decoded, Order.class);
		Order round = JSON.parse(encoded, Order.class);

		// the same value written as a JSON number instead, which JSON.parse rounds before anything
		// here runs
		String asNumber = "{\"sku\":\"A-1\",\"quantity\":0,\"total\":9007199254740993," +
			"\"weight\":0,\"paid\":false,\"status\":\"PENDING\",\"tags\":[]}";
		long lossy = JSON.parse(asNumber, Order.class).total();

		return Bytebox.json(
			json(
				pair("sku", decoded.sku()),
				pair("quantity", String.valueOf(decoded.quantity())),
				pair("total", String.valueOf(decoded.total())),
				pair("weight", String.valueOf(decoded.weight())),
				pair("paid", String.valueOf(decoded.paid())),
				pair("status", decoded.status().name()),
				pair("tags", String.join("|", decoded.tags())),
				pair("roundTrips", String.valueOf(decoded.equals(round))),
				pair("handles", String.valueOf(JSON.handles(Order.class))),
				pair("numberFormRounds", String.valueOf(lossy)),
				pair("encoded", encoded)
			)
		);
	}

	private Response thrown() {
		throw new IllegalStateException("the handler threw");
	}

	private Response missing(Env env) {
		try {
			// a name no lane declares, so this stays a refusal however many bindings are supplied
			env.kv("NOT_DECLARED");
			return Bytebox.response("resolved a binding that is not declared", 500);
		} catch (IllegalStateException named) {
			return Bytebox.json(json(pair("message", named.getMessage())));
		}
	}

	/**
	 * Every binding whose Java surface is a call out to the platform.
	 *
	 * <p>Four of these have no local simulation at all, so the lane supplies an object with the
	 * methods the platform would have. What is being checked is the Java half either way: the
	 * arguments it marshals, the promise it waits on, and what it reads back.
	 */
	private Response bindings(Env env) {
		TSObject inference = env.ai().run("@cf/meta/llama-3", TSObject.object());

		Vectorize vectors = env.vectorize();
		TSObject matched = vectors.query(new double[] { 0.1, 0.2 }, 3);
		vectors.insert(TSObject.object());
		vectors.upsert(TSObject.object());
		vectors.getByIds("a", "b");
		vectors.deleteByIds("a");
		TSObject described = vectors.describe();

		Workflow.Instance run = env.workflow().start(TSObject.object());
		TSObject status = run.status();
		run.pause();
		run.resume();
		run.sendEvent("ping", TSObject.object());
		run.terminate();

		Queue queue = env.queue();
		queue.send(TSObject.object());
		queue.sendJson("{\"queued\":true}");
		queue.send(TSObject.object(), 30);
		queue.sendBatch(TSObject.object(), TSObject.object());

		env
			.analytics()
			.write(AnalyticsEngine.DataPoint.of().index("core").blob("fixture").number(1.5));

		env.email().send("from@example.com", "to@example.com", "subject", "body");

		Hyperdrive database = env.hyperdrive();

		return Bytebox.json(
			json(
				pair("inference", inference.get("answer").asString()),
				pair("matched", String.valueOf(matched.get("count").asInt())),
				pair("dimensions", String.valueOf(described.get("dimensions").asInt())),
				pair("workflow", run.id()),
				pair("status", status.get("status").asString()),
				pair("allowed", String.valueOf(env.rateLimit().allow("an-address"))),
				pair("secret", env.secret().value()),
				pair("searched", env.aiSearch().search("what").get("found").asString()),
				pair("asked", env.aiSearch().ask("why").get("found").asString()),
				pair("host", database.getHost()),
				pair("port", String.valueOf(database.getPort())),
				pair("database", database.getDatabase())
			)
		);
	}

	/**
	 * A real SMTP conversation, against the mail server the compose file runs.
	 *
	 * <p>Nothing is stood in for here: the platform opens the connection, the bytes cross a socket,
	 * and the server on the other end answers. A deployed Worker cannot dial a private address, which
	 * is why this route belongs to the lane that has the container rather than to the gate.
	 *
	 * @param host where the mail server is
	 * @param port its SMTP port
	 */
	private Response socket(String host, int port) {
		StringBuilder conversation = new StringBuilder();
		try (Socket smtp = Sockets.connect(host, port)) {
			conversation.append(smtp.readUntil("\r\n"));
			smtp.write("EHLO fixture\r\n");
			conversation.append('|').append(smtp.readUntil("\r\n"));
			smtp.write("QUIT\r\n");
		}
		// the option only says a later upgrade is allowed, so the connection itself is still plain
		try (Socket upgradable = Sockets.connectStartTLS(host, port)) {
			conversation.append('|').append(upgradable.readUntil("\r\n"));
		}
		return Bytebox.json(json(pair("conversation", conversation.toString())));
	}

	/**
	 * Java arrays crossing to JavaScript and back, which is the widest untested seam there was.
	 *
	 * <p>Every {@code byte[]} overload in core goes through the interop's staging buffer in linear
	 * memory, so this route stops answering entirely when that heap is sized to nothing. It takes no
	 * credentials and no network, which is why it belongs in the gate rather than beside the socket
	 * routes.
	 */
	private Response bytes(Env env) {
		byte[] source = { 0x00, 0x7f, (byte) 0x80, (byte) 0xff, 0x2a };
		byte[] roundTripped = dev.gmitch215.bytebox.js.Bytes.fromBuffer(
			dev.gmitch215.bytebox.js.Bytes.toBuffer(source)
		);
		byte[] windowed = dev.gmitch215.bytebox.js.Bytes.fromView(
			dev.gmitch215.bytebox.js.Bytes.toUnsignedView(source)
		);
		byte[] encoded = dev.gmitch215.bytebox.js.Bytes.fromView(Text.encode("héllo"));

				env.kv().putBytes("raw", source);
		byte[] fromKv = dev.gmitch215.bytebox.js.Bytes.fromBuffer(env.kv().getBytes("raw"));
		env.kv().delete("raw");

		env.r2().putBytes("raw", source);
		byte[] fromR2 = dev.gmitch215.bytebox.js.Bytes.fromBuffer(env.r2().getBytes("raw"));
		env.r2().delete("raw");

		return Bytebox.json(
			json(
				pair("roundTrip", Text.toHex(roundTripped)),
				pair("windowed", Text.toHex(windowed)),
				pair("decoded", Text.decode(encoded)),
				pair("digest", Text.toHex(Crypto.sha256(source)).substring(0, 16)),
				pair("equal", String.valueOf(Crypto.timingSafeEquals(source, roundTripped))),
				pair("differ", String.valueOf(Crypto.timingSafeEquals(source, encoded))),
				pair("fromKv", Text.toHex(fromKv)),
				pair("fromR2", Text.toHex(fromR2))
			)
		);
	}

	@JSBody(script = "return Promise.reject(new Error('rejected on purpose'));")
	static native JSPromise<JSObject> rejectingError();

	@JSBody(script = "return Promise.reject('a bare string');")
	static native JSPromise<JSObject> rejectingString();

	@JSBody(script = "return Promise.reject(undefined);")
	static native JSPromise<JSObject> rejectingNothing();

	/**
	 * Every shape a promise can reject with, each awaited from Java.
	 *
	 * <p>An {@code Error} is the one that used to hang: the compiler's own rejection path asks the
	 * runtime to unwrap a Java throwable that is not there, and the answer is not a value the
	 * boundary accepts. Every platform call goes through this, so a binding that refuses would take
	 * the whole invocation with it.
	 */
	private Response rejects() {
		return Bytebox.json(
			json(
				pair("error", outcomeOf(rejectingError())),
				pair("string", outcomeOf(rejectingString())),
				pair("nothing", outcomeOf(rejectingNothing()))
			)
		);
	}

	private String outcomeOf(JSPromise<JSObject> promise) {
		try {
			Async.awaitVoid(promise);
			return "returned";
		} catch (Throwable caught) {
			return caught.getClass().getSimpleName();
		}
	}

	/** A route that declares a checked exception, since the handler signature declares none. */
	private interface Route {
		Response answer() throws Exception;
	}

	private Response unchecked(Route route) {
		try {
			return route.answer();
		} catch (Exception failed) {
			return Bytebox.json(json(pair("failed", String.valueOf(failed))), 500);
		}
	}

	/**
	 * The platform's own fetch, and the response conveniences layered over what it answers.
	 *
	 * <p>Every outbound request from this Worker reaches the echo service the lane registers, so
	 * nothing here leaves the machine.
	 */
	private Response fetch(String base) {
		Response plain = Bytebox.fetch(base + "/echo?q=1");
		TSObject parsed = plain.assertOk().json();

		TSObject options = TSObject.object();
		options.set("method", TSObject.of("POST"));
		options.set("body", TSObject.of("sent with options"));
		Response posted = Bytebox.fetch(base + "/echo", options);
		String mapped = posted.json(document -> document.get("body").asString());

		Response viaRequest = Bytebox.fetch(
			Bytebox.request(base + "/echo", "GET", null, TSObject.object())
		);
		byte[] raw = dev.gmitch215.bytebox.js.Bytes.fromBuffer(viaRequest.bytes());

		String refusedOutright;
		try {
			Bytebox.fetch(base + "/boom");
			refusedOutright = "a rejected fetch returned";
		} catch (Throwable caught) {
			refusedOutright = caught.getClass().getSimpleName();
		}

		Response failing = Bytebox.fetch(base + "/status/503");
		String refused;
		try {
			failing.assertOk();
			refused = "assertOk let a 503 through";
		} catch (IllegalStateException caught) {
			refused = caught.getMessage();
		}

		Response built = Bytebox.bytes(new byte[] { 1, 2, 3 }, "application/octet-stream");
		Response fromValue = Bytebox.json(TSObject.fromJson("{\"built\":true}"));
		Response fromCodec = Bytebox.json(
			new Order("A-1", 1, 2L, 0.5, true, Order.Status.PENDING, List.of("x")),
			Order.class
		);

		return Bytebox.json(
			json(
				pair("method", parsed.get("method").asString()),
				pair("query", parsed.get("query").asString()),
				pair("echoed", plain.getHeaders().get("x-echo")),
				pair("posted", mapped),
				pair("rawLength", String.valueOf(raw.length)),
				pair("refused", refused),
				pair("refusedOutright", refusedOutright),
				pair("builtType", built.getHeaders().get("content-type")),
				pair("builtStatus", String.valueOf(fromValue.getStatus())),
				pair("codec", fromCodec.text())
			)
		);
	}

	/** {@code java.net.URL} and {@code HttpURLConnection}, which is the older way a library asks. */
	private Response urlConnection(String base) throws Exception {
		java.net.URL url = new java.net.URL(base + "/echo?from=urlconnection");
		java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("x-fixture", "urlconnection");
		connection.setDoOutput(true);
		connection.setConnectTimeout(1000);
		connection.setReadTimeout(1000);
		connection.getOutputStream().write("ping".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		int status = connection.getResponseCode();
		String message = connection.getResponseMessage();
		String body = new String(
			connection.getInputStream().readAllBytes(),
			java.nio.charset.StandardCharsets.UTF_8
		);
		String seen = connection.getHeaderField("x-echo");
		connection.disconnect();

		java.net.HttpURLConnection failing = (java.net.HttpURLConnection) new java.net.URL(
			base + "/status/404"
		).openConnection();
		int failed = failing.getResponseCode();
		String error = new String(
			failing.getErrorStream().readAllBytes(),
			java.nio.charset.StandardCharsets.UTF_8
		);

		return Bytebox.json(
			json(
				pair("status", String.valueOf(status)),
				pair("message", String.valueOf(message)),
				pair("host", url.getHost()),
				pair("path", url.getPath()),
				pair("query", url.getQuery()),
				pair("protocol", url.getProtocol()),
				pair("external", url.toExternalForm()),
				pair("seen", String.valueOf(seen)),
				pair("body", body),
				pair("failed", String.valueOf(failed)),
				pair("error", error)
			)
		);
	}

	/** {@code java.net.http.HttpClient}, which is the current one. */
	private Response httpClient(String base) throws Exception {
		java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(2))
			.followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
			.build();

		java.net.http.HttpRequest posted = java.net.http.HttpRequest.newBuilder(
			java.net.URI.create(base + "/echo")
		)
			.header("x-fixture", "httpclient")
			.timeout(java.time.Duration.ofSeconds(2))
			.POST(java.net.http.HttpRequest.BodyPublishers.ofString("pong"))
			.build();
		java.net.http.HttpResponse<String> answer = client.send(
			posted,
			java.net.http.HttpResponse.BodyHandlers.ofString()
		);

		java.net.http.HttpResponse<byte[]> bytes = client.send(
			java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/echo"))
				.GET()
				.build(),
			java.net.http.HttpResponse.BodyHandlers.ofByteArray()
		);

		java.net.http.HttpResponse<Void> discarded = java.net.http.HttpClient.newHttpClient().send(
			java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(base + "/status/204"))
				.DELETE()
				.build(),
			java.net.http.HttpResponse.BodyHandlers.discarding()
		);

		return Bytebox.json(
			json(
				pair("status", String.valueOf(answer.statusCode())),
				pair("body", answer.body()),
				pair("method", posted.method()),
				pair("uri", answer.uri().toString()),
				pair("header", answer.headers().firstValue("x-echo").orElse("none")),
				pair("byteLength", String.valueOf(bytes.body().length)),
				pair("discarded", String.valueOf(discarded.statusCode())),
				pair("redirects", client.followRedirects().name())
			)
		);
	}

	/** The bindings that are a callable service rather than a store. */
	private Response services(Env env) {
		TSObject called = env.service().fetch("http://service.test/echo").json();
		TSObject viaMtls = env.mtls().fetch("http://mtls.test/echo").json();
		TSObject viaBrowser = env.browser().fetch("http://browser.test/echo").json();

		TSObject options = TSObject.object();
		options.set("method", TSObject.of("PUT"));
		TSObject withOptions = env.service().fetch("http://service.test/echo", options).json();

		env.pipeline().sendJson("{\"piped\":true}");
		env.pipeline().send(TSObject.object(), TSObject.object());

		TSObject info = env.images().info(TSObject.of("image bytes"));
		TSObject rendered = env
			.images()
			.input(TSObject.of("image bytes"))
			.transform(TSObject.object())
			.output(TSObject.object());

		TSObject byRequest = env
			.service()
			.fetch(Bytebox.request("http://service.test/echo", "GET", null, TSObject.object()))
			.json();
		TSObject viaRpc = env.service().rpc("fetch", TSObject.of("http://service.test/echo"));

		TSObject withOptions2 = env.ai().run("@cf/meta/llama-3", TSObject.object(), TSObject.object());
		TSObject embedded = env.ai().embed("@cf/baai/bge-base-en", "some text");

		dev.gmitch215.bytebox.binding.Container sidecar = env.container("SIDECAR");
		boolean before = sidecar.isRunning();
		sidecar.start();
		sidecar.start(TSObject.object());
		sidecar.stop();
		sidecar.signal(9);

		return Bytebox.json(
			json(
				pair("service", called.get("path").asString()),
				pair("mtls", viaMtls.get("path").asString()),
				pair("browser", viaBrowser.get("path").asString()),
				pair("method", withOptions.get("method").asString()),
				pair("format", info.get("format").asString()),
				pair("width", String.valueOf(info.get("width").asInt())),
				pair("rendered", String.valueOf(rendered != null)),
				pair("byRequest", byRequest.get("path").asString()),
				pair("rpc", String.valueOf(viaRpc != null)),
				pair("aiOptions", withOptions2.get("answer").asString()),
				pair("embedded", embedded.get("answer").asString()),
				pair("running", String.valueOf(before)),
				pair("port", String.valueOf(sidecar.getTcpPort(8080).get("port").asInt()))
			)
		);
	}

	/** Everything a future can be built from and everything it can be turned into. */
	private Response futures() {
		Future<TSObject> ready = Future.completed(TSObject.of("ready"));
		Future<TSObject> broken = Future.failed(new IllegalStateException("built failed"));
		Future<TSObject> wrapped = Future.of(ready.promise());

		String mapped = ready.map(value -> TSObject.of(value.asString() + " mapped")).join()
			.asString();
		String chained = ready.then(value -> Future.completed(TSObject.of("then"))).join()
			.asString();
		String recovered = broken
			.recover(failure -> TSObject.of("recovered " + failure.getClass().getSimpleName()))
			.join()
			.asString();
		String fallback = broken.orElseGet(() -> TSObject.of("supplied")).asString();

		List<TSObject> both = Async.all(
			JSPromise.resolve(TSObject.of("first")),
			JSPromise.resolve(TSObject.of("second"))
		);
		String won = Async.race(
			JSPromise.resolve(TSObject.of("won")),
			JSPromise.resolve(TSObject.of("lost"))
		).asString();

		StringBuilder ran = new StringBuilder();
		Async.run(() -> ran.append("side effect")).join();

		return Bytebox.json(
			json(
				pair("ready", ready.join().asString()),
				pair("wrapped", wrapped.join().asString()),
				pair("mapped", mapped),
				pair("chained", chained),
				pair("recovered", recovered),
				pair("fallback", fallback),
				pair("all", String.join(",", both.get(0).asString(), both.get(1).asString())),
				pair("race", won),
				pair("ran", ran.toString())
			)
		);
	}

	/**
	 * The siblings of everything the routes above already reach.
	 *
	 * <p>Each of these is a one-line convenience over a method that is already covered, and each is
	 * the kind of surface that breaks by being spelled wrong rather than by being wrong. The route is
	 * one call apiece so a rename that misses one shows up here.
	 */
	private Response overloads(Env env) {
		byte[] key = { 1, 2, 3 };
		byte[] data = "signed".getBytes(java.nio.charset.StandardCharsets.UTF_8);

		KVNamespace kv = env.kv();
		kv.put("json", "{\"kv\":true}");
		kv.put("ttl", "expiring", 120);
		kv.putWithMetadata("tagged", "value", TSObject.fromJson("{\"tag\":\"a\"}"));
		kv.putBytes("buffer", dev.gmitch215.bytebox.js.Bytes.toBuffer(key));
		TSObject asJson = kv.getJson("json");
		KVNamespace.Entry tagged = kv.getWithMetadata("tagged");
		int listed = kv.list().getKeys().getLength();
		int prefixed = kv.list("ta").getKeys().getLength();
		int paged = kv.list("ta", null, 10).getKeys().getLength();

		R2Bucket blob = env.r2();
		blob.put("plain.txt", "eight ok");
		blob.putBytes("bytes.bin", dev.gmitch215.bytebox.js.Bytes.toBuffer(key));
		blob.putBytes(
			"typed.bin",
			dev.gmitch215.bytebox.js.Bytes.toBuffer(key),
			"application/octet-stream",
			null
		);
		String ranged = blob.getRange("plain.txt", 0, 5).text();
		int all = blob.list().getObjects().getLength();
		int under = blob.list("pl").getObjects().getLength();
		int page = blob.list("pl", null, 10).getObjects().getLength();
		List<String> names = blob.listAll("");
		blob.delete("plain.txt", "bytes.bin", "typed.bin");

		D1Database db = env.d1();
		db.exec("create table if not exists blobs (id integer primary key, body blob, flag integer)");
		db.exec("delete from blobs");
		db.prepare("insert into blobs (id, body, flag) values (?, ?, ?)").bind(1, key, true).run();
		TSObject stored = db.prepare("select flag from blobs where id = ?").bind(1).first();
		TSObject column = db.prepare("select flag from blobs where id = ?").bind(1).first("flag");
		List<D1Database.D1Result> batched = db.batch(
			db.prepare("select id from blobs"),
			db.prepare("select flag from blobs")
		);

		return Bytebox.json(
			json(
				pair("md5", Text.toHex(Crypto.md5("a")).length() > 0 ? "ok" : "empty"),
				pair("sha1", Text.toHex(Crypto.sha1("a")).substring(0, 8)),
				pair("sha512", String.valueOf(Text.toHex(Crypto.sha512("a")).length())),
				pair("md5Bytes", String.valueOf(Text.toHex(Crypto.md5(key)).length())),
				pair("sha1Bytes", String.valueOf(Text.toHex(Crypto.sha1(key)).length())),
				pair("sha512Bytes", String.valueOf(Text.toHex(Crypto.sha512(key)).length())),
				pair("digest", String.valueOf(Text.toHex(Crypto.digest("SHA-256", key)).length())),
				pair("hmac", Text.toHex(Crypto.hmacSha256("secret", "message")).substring(0, 8)),
				pair(
					"hmacBytes",
					String.valueOf(Text.toHex(Crypto.hmac("SHA-256", key, data)).length())
				),
				pair("sameText", String.valueOf(Crypto.timingSafeEquals("abc", "abc"))),
				pair("randomBytes", String.valueOf(Crypto.random32().getByteLength())),
				pair("decoded", Text.decode(data, "utf-8")),
				pair("base64", Text.toBase64(key)),
				pair("cookies", String.valueOf(Bytebox.headers().setCookies().size())),
				pair("kvJson", String.valueOf(asJson.get("kv").asBoolean())),
				pair("kvTag", tagged.getMetadata().get("tag").asString()),
				pair("kvListed", String.valueOf(listed > 0 && prefixed > 0 && paged > 0)),
				pair("r2Range", ranged),
				pair("r2Listed", String.valueOf(all > 0 && under > 0 && page > 0)),
				pair("r2Names", String.valueOf(names.size() > 0)),
				pair("d1Flag", String.valueOf(stored.get("flag").asInt())),
				pair("d1Column", String.valueOf(column.asInt())),
				pair("d1Batch", String.valueOf(batched.size()))
			)
		);
	}

	/**
	 * A Durable Object, reached the way a Worker reaches one.
	 *
	 * <p>The stub is a handle rather than a round trip, so the identity reads are free and only the
	 * fetches cross. Storage, SQL and the alarm all run inside the instance.
	 */
	private Response durable(Env env) {
		DurableObjectNamespace counters = env.durableObject("DO_COUNTER");
		DurableObjectNamespace.DurableObjectId named = counters.idFromName("global");
		DurableObjectNamespace.DurableObjectStub stub = counters.get(named);
		DurableObjectNamespace.DurableObjectId parsed = counters.idFromString(named.asString());
		DurableObjectNamespace.DurableObjectId unique = counters.newUniqueId();

		TSObject storage = stub.fetch("https://counter/storage").json();
		TSObject sql = counters.byName("global").fetch("https://counter/sql").json();
		TSObject alarm = stub.fetch("https://counter/alarm").json();

		return Bytebox.json(
			json(
				pair("name", named.name()),
				pair("stable", String.valueOf(named.asString().equals(parsed.asString()))),
				pair("unique", String.valueOf(!unique.asString().equals(named.asString()))),
				pair("stubId", String.valueOf(stub.getId().asString().equals(named.asString()))),
				pair("count", storage.get("count").asString()),
				pair("nested", storage.get("nested").asString()),
				pair("removed", storage.get("removed").asString()),
				pair("again", storage.get("again").asString()),
				pair("rows", sql.get("rows").asString()),
				pair("first", sql.get("first").asString()),
				pair("seen", sql.get("seen").asString()),
				pair("alarmSet", String.valueOf(!alarm.get("set").asString().equals("0"))),
				pair("alarmCleared", alarm.get("cleared").asString())
			)
		);
	}

	/**
	 * A real TLS handshake, against the certificate the compose file's endpoint serves.
	 *
	 * <p>Both ways of getting there: encrypted from the first byte, and a plain connection upgraded in
	 * place. The certificate is self-signed, so the lane hands it to the runtime through
	 * {@code NODE_EXTRA_CA_CERTS} rather than disabling verification.
	 *
	 * @param host where the endpoint is
	 * @param port its TLS port
	 */
	private Response tls(String host, int port) {
		String direct;
		try (Socket encrypted = Sockets.connectTLS(host, port)) {
			encrypted.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n");
			direct = encrypted.readUntil("\r\n");
		}

		String upgraded;
		Socket plain = Sockets.connectStartTLS(host, port);
		try (Socket secured = plain.startTLS()) {
			secured.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n");
			upgraded = secured.readUntil("\r\n");
		}

		// the same endpoint through the class a library would use, which takes the flag rather than a
		// separate factory
		String jdk;
		try (java.net.Socket socket = new java.net.Socket(host, port, true)) {
			socket
				.getOutputStream()
				.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes(
					java.nio.charset.StandardCharsets.UTF_8
				));
			byte[] buffer = new byte[32];
			int read = socket.getInputStream().read(buffer);
			jdk = new String(
				buffer,
				0,
				Math.max(read, 0),
				java.nio.charset.StandardCharsets.UTF_8
			).trim();
		} catch (java.io.IOException failed) {
			jdk = "failed: " + failed.getMessage();
		}

		return Bytebox.json(
			json(pair("direct", direct), pair("upgraded", upgraded), pair("jdk", jdk))
		);
	}

	/**
	 * The database a Hyperdrive binding points at, dialled with the details it reports.
	 *
	 * <p>Locally the binding is stood in for but the server is not: the socket carries a real
	 * PostgreSQL startup message and the answer is parsed off the wire, which is what says the
	 * connection details a Worker reads are usable rather than merely readable.
	 */
	private Response hyperdrive(Env env) throws Exception {
		Hyperdrive database = env.hyperdrive();
		try (java.net.Socket socket = new java.net.Socket(database.getHost(), database.getPort())) {
			socket.getOutputStream().write(startup(database.getUser(), database.getDatabase()));
			java.io.InputStream in = socket.getInputStream();
			// every message the server sends is a one-byte tag then a four-byte length
			int tag = in.read();
			byte[] length = new byte[4];
			in.read(length);
			int size =
				((length[0] & 0xFF) << 24) |
				((length[1] & 0xFF) << 16) |
				((length[2] & 0xFF) << 8) |
				(length[3] & 0xFF);
			byte[] payload = new byte[Math.min(size - 4, 64)];
			in.read(payload);

			return Bytebox.json(
				json(
					pair("connectionString", database.getConnectionString()),
					pair("user", database.getUser()),
					pair("database", database.getDatabase()),
					pair("tag", String.valueOf((char) tag)),
					pair("size", String.valueOf(size)),
					pair("payload", String.valueOf(payload.length))
				)
			);
		}
	}

	/** A PostgreSQL startup message: length, protocol 3.0, then null-terminated key/value pairs. */
	private static byte[] startup(String user, String database) {
		java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
		for (String field : List.of("user", user, "database", database, "")) {
			byte[] encoded = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			body.write(encoded, 0, encoded.length);
			body.write(0);
		}
		byte[] fields = body.toByteArray();
		int length = 8 + fields.length;
		java.io.ByteArrayOutputStream message = new java.io.ByteArrayOutputStream();
		message.write(length >> 24);
		message.write(length >> 16);
		message.write(length >> 8);
		message.write(length);
		message.write(new byte[] { 0, 3, 0, 0 }, 0, 4);
		message.write(fields, 0, fields.length);
		return message.toByteArray();
	}

	/**
	 * The same server through {@code java.net.Socket}, which is what an ordinary library reaches for.
	 *
	 * <p>Reads a chunk into a {@code byte[]}, so it crosses the boundary the route above does not: the
	 * bytes come back as a typed array and the interop stages them through linear memory. That is what
	 * makes it the regression test for {@code minDirectBuffersSize}.
	 */
	private Response jdkSocket(String host, int port) {
		return Bytebox.json(json(pair("jdk", jdkConversation(host, port))));
	}

	private String jdkConversation(String host, int port) {
		try (java.net.Socket socket = new java.net.Socket(host, port)) {
			socket.setSoTimeout(2000);
			java.io.OutputStream out = socket.getOutputStream();
			java.io.InputStream in = socket.getInputStream();
			byte[] buffer = new byte[64];
			int read = in.read(buffer);
			String greeting = new String(
				buffer,
				0,
				Math.max(read, 0),
				java.nio.charset.StandardCharsets.UTF_8
			);
			int buffered = in.available();
			out.write('Q');
			out.write("UIT\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			// only what the real java.net.Socket declares, since that is what this compiles against
			return String.join(
				" ",
				String.valueOf(socket.getPort()),
				socket.getInetAddress().getHostName(),
				String.valueOf(socket.getSoTimeout()),
				String.valueOf(socket.isConnected()),
				String.valueOf(socket.isClosed()),
				String.valueOf(buffered),
				greeting.trim()
			);
		} catch (java.io.IOException failed) {
			return "failed: " + failed.getMessage();
		}
	}

	/** The pattern engine, which is the platform's own rather than a copy inside the module. */
	private Response regex() {
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
			"(?<word>\\p{Alpha}+)-(\\d+)"
		);
		java.util.regex.Matcher matcher = pattern.matcher("alpha-1 beta-22 gamma-333");
		StringBuilder found = new StringBuilder();
		while (matcher.find()) {
			if (found.length() > 0) found.append(',');
			found.append(matcher.group("word")).append(':').append(matcher.group(2));
		}
		return Bytebox.json(
			json(
				pair("found", found.toString()),
				pair("replaced", pattern.matcher("alpha-1").replaceAll("<$1>")),
				pair("split", String.join("|", "a1b22c".split("\\d+"))),
				pair("formatted", String.format("%,.2f %s %04d", 1234.5, "x", 7))
			)
		);
	}

	/**  #json(String...) */
	static String field(String key, String value) {
		return pair(key, value);
	}

	/** Builds the same JSON shape from another class in this fixture. */
	static String object(String... fields) {
		return json(fields);
	}

	private static String pair(String key, String value) {
		return "\"" + key + "\":" + quote(value);
	}

	private static String quote(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"' || c == '\\') out.append('\\').append(c);
			else if (c < 0x20) out.append(' ');
			else out.append(c);
		}
		return out.append('"').toString();
	}

	private static String json(String... pairs) {
		return "{" + String.join(",", pairs) + "}";
	}
}
