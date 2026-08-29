package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.durable.AlarmObject;
import dev.gmitch215.bytebox.durable.DurableState;
import dev.gmitch215.bytebox.durable.SQL;
import dev.gmitch215.bytebox.durable.SocketObject;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.socket.WebSocket;
import dev.gmitch215.bytebox.socket.WebSocketPair;
import java.util.List;

/** A Durable Object exercising storage, SQL, alarms and websockets, one route per thing. */
public class Counter implements SocketObject, AlarmObject {

	private final StringBuilder seen = new StringBuilder();
	private String stage = "start";

	@Override
	public Response fetch(Request request, DurableState state, Env env) {
		// a throw from here crosses back through a second module instance, where the runtime cannot
		// read the Java message, so the failure is answered rather than thrown
		try {
			return route(request, state);
		} catch (Throwable failure) {
			return Bytebox.json(
				CoreWorker.object(
					CoreWorker.field("failed", failure.getClass().getName()),
					CoreWorker.field("message", String.valueOf(failure.getMessage())),
					CoreWorker.field("stage", stage)
				),
				500
			);
		}
	}

	private Response route(Request request, DurableState state) {
		return switch (request.path()) {
			case "/storage" -> storage(state);
			case "/sql" -> sql(state);
			case "/alarm" -> alarm(state);
			case "/socket", "/durablesocket" -> upgrade(state);
			case "/seen" -> Bytebox.response(seen.toString());
			default -> Bytebox.response("no durable route for " + request.path(), 404);
		};
	}

	private Response storage(DurableState state) {
		stage = "put text";
		state.put("greeting", "hello from storage");
		stage = "put long";
		state.put("count", 41L);
		stage = "put object";
		state.put("shape", TSObject.fromJson("{\"nested\":true}"));
		stage = "get long";
		long bumped = state.getLong("count", 0) + 1;
		state.put("count", bumped);

		stage = "get string";
		String greeting = state.getString("greeting");
		TSObject shape = state.get("shape");
		stage = "keys";
		List<String> keys = state.keys();
		stage = "delete";
		boolean removed = state.delete("greeting");
		boolean again = state.delete("greeting");

		stage = "identifier";
		return Bytebox.json(
			CoreWorker.object(
				CoreWorker.field("greeting", greeting),
				CoreWorker.field("count", String.valueOf(bumped)),
				CoreWorker.field("nested", String.valueOf(shape.get("nested").asBoolean())),
				CoreWorker.field("keys", String.valueOf(keys.size())),
				CoreWorker.field("removed", String.valueOf(removed)),
				CoreWorker.field("again", String.valueOf(again)),
				CoreWorker.field("id", state.identifier())
			)
		);
	}

	private Response sql(DurableState state) {
		state.sql("create table if not exists hits (name text primary key, seen integer)");
		state.sql("delete from hits");
		state.sql("insert into hits (name, seen) values (?, ?)", "core", 2);
		state.sql("insert into hits (name, seen) values (?, ?)", "plugin", 5);
		SQL rows = state.sql("select name, seen from hits order by name");
		List<TSObject> all = rows.rows();
		TSObject first = state.sql("select seen from hits where name = ?", "core").one();

		return Bytebox.json(
			CoreWorker.object(
				CoreWorker.field("rows", String.valueOf(all.size())),
				CoreWorker.field("first", all.get(0).get("name").asString()),
				CoreWorker.field("seen", String.valueOf(first.get("seen").asInt()))
			)
		);
	}

	private Response alarm(DurableState state) {
		state.setAlarm(1);
		long at = state.alarmAt();
		state.deleteAlarm();
		long cleared = state.alarmAt();

		return Bytebox.json(
			CoreWorker.object(
				CoreWorker.field("set", String.valueOf(at)),
				CoreWorker.field("cleared", String.valueOf(cleared))
			)
		);
	}

	private Response upgrade(DurableState state) {
		WebSocketPair pair = WebSocketPair.create();
		WebSocket server = pair.server();
		state.acceptWebSocket(server);
		return Bytebox.upgrade(pair.client());
	}

	@Override
	public void alarm(DurableState state, Env env) {
		seen.append("alarm|");
	}

	@Override
	public void message(WebSocket socket, String text, DurableState state, Env env) {
		seen.append("text:").append(text).append('|');
		socket.send("echo " + text);
		if (text.equals("count")) {
			socket.send("sockets " + state.sockets().size());
			state.broadcast("broadcast");
		}
	}

	@Override
	public void message(WebSocket socket, byte[] bytes, DurableState state, Env env) {
		seen.append("bytes:").append(bytes.length).append('|');
		socket.send(bytes);
	}

	@Override
	public void closed(WebSocket socket, int code, String reason, boolean clean, DurableState state, Env env) {
		seen.append("closed:").append(code).append('|');
	}

	@Override
	public void failed(WebSocket socket, String message, DurableState state, Env env) {
		seen.append("failed|");
	}
}
