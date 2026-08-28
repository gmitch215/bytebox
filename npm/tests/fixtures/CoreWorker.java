package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.binding.D1Database;
import dev.gmitch215.bytebox.binding.KVNamespace;
import dev.gmitch215.bytebox.binding.R2Bucket;
import dev.gmitch215.bytebox.builtin.Clock;
import dev.gmitch215.bytebox.builtin.Crypto;
import dev.gmitch215.bytebox.builtin.Intl;
import dev.gmitch215.bytebox.builtin.Text;
import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.concurrent.Future;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.json.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A worker that exercises core against real bindings, one route per thing being proved. */
public class CoreWorker implements Worker {

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
			case "/tsobject" -> tsobject();
			case "/numbers" -> numbers();
			case "/collections" -> collections();
			case "/json" -> json();
			case "/throws" -> thrown();
			case "/missing" -> missing(env);
			default -> Bytebox.response("no route for " + request.path(), 404);
		};
	}

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
		Order decoded = Json.parse(source, Order.class);
		String encoded = Json.stringify(decoded, Order.class);
		Order round = Json.parse(encoded, Order.class);

		// the same value written as a JSON number instead, which JSON.parse rounds before anything
		// here runs
		String asNumber = "{\"sku\":\"A-1\",\"quantity\":0,\"total\":9007199254740993," +
			"\"weight\":0,\"paid\":false,\"status\":\"PENDING\",\"tags\":[]}";
		long lossy = Json.parse(asNumber, Order.class).total();

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
				pair("handles", String.valueOf(Json.handles(Order.class))),
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
			env.vectorize();
			return Bytebox.response("resolved a binding that is not declared", 500);
		} catch (IllegalStateException named) {
			return Bytebox.json(json(pair("message", named.getMessage())));
		}
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
