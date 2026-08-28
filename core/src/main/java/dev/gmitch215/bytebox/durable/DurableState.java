package dev.gmitch215.bytebox.durable;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.socket.WebSocket;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;

/**
 * A Durable Object's own context: its storage, its alarm, and its WebSockets.
 *
 * <p>Handed to every handler. Storage is strongly consistent and private to this instance, and an
 * instance is single-threaded, so a read-modify-write needs no lock.
 *
 * {@snippet lang = "java":
 * public class Counter implements DurableObject {
 * 	@Override
 * 	public Response fetch(Request request, DurableState state, Env env) {
 * 		long count = state.getLong("count", 0) + 1;
 * 		state.put("count", count);
 * 		return Bytebox.response(String.valueOf(count));
 * 	}
 * }
 *}
 *
 * @since 1.0.0
 */
public interface DurableState extends JSObject {
	/** {@return the storage itself, for anything the convenience methods here do not cover} */
	@JSProperty("storage")
	Storage storage();

	// #region storage

	/**
	 * Reads a value.
	 *
	 * @param key the key
	 * @return the value, or {@code null} when absent
	 */
	default TSObject get(String key) {
		return Async.await(storage().read(key));
	}

	/**
	 * Reads a number.
	 *
	 * @param key the key
	 * @param fallback what to answer when the key is absent
	 * @return the value
	 */
	default long getLong(String key, long fallback) {
		TSObject value = get(key);
		return value == null || value.isNull() ? fallback : value.asLong();
	}

	/**
	 * Reads text.
	 *
	 * @param key the key
	 * @return the value, or {@code null} when absent
	 */
	default String getString(String key) {
		TSObject value = get(key);
		return value == null || value.isNull() ? null : value.asString();
	}

	/**
	 * Writes a value.
	 *
	 * @param key the key
	 * @param value the value
	 */
	default void put(String key, TSObject value) {
		Async.awaitVoid(storage().write(key, value));
	}

	/**
	 * Writes text.
	 *
	 * @param key the key
	 * @param value the value
	 */
	default void put(String key, String value) {
		put(key, TSObject.of(value));
	}

	/**
	 * Writes a number.
	 *
	 * <p>Stored as a JavaScript number, so a value past 2^53 loses precision. Write one that has to
	 * survive exactly with {@code String.valueOf} instead.
	 *
	 * @param key the key
	 * @param value the value
	 */
	default void put(String key, long value) {
		put(key, TSObject.ofNumber(value));
	}

	/**
	 * Removes a key.
	 *
	 * @param key the key
	 * @return whether it was there
	 */
	default boolean delete(String key) {
		TSObject removed = Async.await(storage().remove(key));
		return removed != null && !removed.isNull() && removed.asBoolean();
	}

	/** {@return every key this instance holds} */
	default List<String> keys() {
		return Durables.keys(Async.await(storage().entries()));
	}

	/** Removes everything this instance holds, including the alarm. */
	default void deleteAll() {
		Async.awaitVoid(storage().removeAll());
	}

	// #endregion

	// #region sql

	/**
	 * Runs a query against this instance's own SQLite database.
	 *
	 * <p>Synchronous, unlike D1: the database is local to this instance, so there is no promise to
	 * wait on and no fiber suspension. Bind parameters are {@code ?1}, {@code ?2} and so on.
	 *
	 * {@snippet lang = "java":
	 * state.sql("create table if not exists visits (day text primary key, count integer)");
	 * long count = state.sql("select count from visits where day = ?1", day)
	 * 	.one()
	 * 	.get("count")
	 * 	.asLong();
	 *}
	 *
	 * @param query the SQL
	 * @param bindings the values to bind, in order
	 * @return what the query answered
	 */
	default SQL sql(String query, Object... bindings) {
		TSObject values = TSObject.array();
		for (Object binding : bindings) values.push(TSObject.from(binding));
		return Durables.exec(storage(), query, values);
	}

	// #endregion

	// #region the alarm

	/**
	 * Schedules the alarm handler.
	 *
	 * <p>One alarm per instance: setting it again moves the time rather than adding a second. The
	 * handler is retried on a throw, so it has to be safe to run twice.
	 *
	 * @param at when, in milliseconds since the epoch
	 */
	default void setAlarm(long at) {
		Async.awaitVoid(storage().scheduleAlarm(at));
	}

	/** {@return when the alarm is due, in milliseconds since the epoch, or 0 when none is set} */
	default long alarmAt() {
		TSObject at = Async.await(storage().readAlarm());
		return at == null || at.isNull() ? 0L : at.asLong();
	}

	/** Cancels the alarm. */
	default void deleteAlarm() {
		Async.awaitVoid(storage().removeAlarm());
	}

	// #endregion

	// #region sockets

	/**
	 * Takes the server end of a WebSocket into this instance's set.
	 *
	 * <p>Accepted this way rather than with the socket's own {@code accept}, because this is what lets
	 * the instance hibernate: the connection stays open with no instance in memory, and a message
	 * brings one back.
	 *
	 * @param socket the server end
	 */
	@JSMethod("acceptWebSocket")
	void acceptWebSocket(WebSocket socket);

	/**
	 * Takes the server end into this instance's set, under tags.
	 *
	 * @param socket the server end
	 * @param tags labels to find it by later, at most 10 of at most 256 characters
	 */
	@JSMethod("acceptWebSocket")
	void acceptWebSocket(WebSocket socket, JSObject tags);

	/** {@return every WebSocket this instance holds} */
	default List<WebSocket> sockets() {
		JSArrayReader<WebSocket> held = webSockets();
		List<WebSocket> sockets = new ArrayList<>(held.getLength());
		for (int i = 0; i < held.getLength(); i++) sockets.add(held.get(i));
		return sockets;
	}

	/**
	 * Sends text to every open WebSocket this instance holds.
	 *
	 * @param message the text
	 * @return how many it reached
	 */
	default int broadcast(String message) {
		int sent = 0;
		for (WebSocket socket : sockets()) {
			if (!socket.isOpen()) continue;
			socket.send(message);
			sent++;
		}
		return sent;
	}

	@JSMethod("getWebSockets")
	JSArrayReader<WebSocket> webSockets();

	// #endregion

	/** {@return this instance's identifier, stable for the life of the object} */
	default String identifier() {
		return Durables.identifier(this);
	}

	/** A Durable Object's storage. */
	interface Storage extends JSObject {
		@JSMethod("get")
		JSPromise<TSObject> read(String key);

		@JSMethod("put")
		JSPromise<JSObject> write(String key, TSObject value);

		@JSMethod("delete")
		JSPromise<TSObject> remove(String key);

		@JSMethod("deleteAll")
		JSPromise<JSObject> removeAll();

		@JSMethod("list")
		JSPromise<JSObject> entries();

		@JSMethod("setAlarm")
		JSPromise<JSObject> scheduleAlarm(double at);

		@JSMethod("getAlarm")
		JSPromise<TSObject> readAlarm();

		@JSMethod("deleteAlarm")
		JSPromise<JSObject> removeAlarm();
	}
}
