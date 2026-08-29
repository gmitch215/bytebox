package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * A Workers KV namespace. Declared with {@code kv()}, named {@code KV} by default.
 *
 * <p>Reads are eventually consistent and cached at the edge for at least 60 seconds. A write is
 * visible in the region that made it straight away and elsewhere within about a minute, so a
 * read-after-write in another region can return the old value.
 *
 * <p>Every method here blocks and returns a value. Nothing needs a callback or a future.
 *
 * {@snippet lang = "java":
 * KVNamespace kv = env.kv();
 * String visits = kv.get("visits");
 * kv.put("visits", String.valueOf(visits == null ? 1 : Integer.parseInt(visits) + 1));
 *}
 *
 * @since 1.0.0
 */
public interface KVNamespace extends JSObject {
	/**
	 * Reads a value as text.
	 *
	 * @param key the key
	 * @return the value, or {@code null} when the key is absent
	 */
	default String get(String key) {
		JSString value = Async.await(getText(key));
		return value == null ? null : value.stringValue();
	}

	/**
	 * Reads a value and parses it as JSON.
	 *
	 * @param key the key
	 * @return the parsed value, or {@code null} when the key is absent
	 */
	default TSObject getJson(String key) {
		return Async.await(getWithType(key, "json"));
	}

	/**
	 * Reads a value as bytes.
	 *
	 * @param key the key
	 * @return the bytes, or {@code null} when the key is absent
	 */
	default ArrayBuffer getBytes(String key) {
		return Async.await(getBuffer(key, "arrayBuffer"));
	}

	/**
	 * Reads a value together with the metadata stored beside it.
	 *
	 * @param key the key
	 * @return the value and its metadata; {@link Entry#value()} is {@code null} when absent
	 */
	default Entry getWithMetadata(String key) {
		return Async.await(getMetadata(key, "text"));
	}

	/**
	 * Writes a value.
	 *
	 * @param key the key
	 * @param value the value
	 */
	default void put(String key, String value) {
		Async.awaitVoid(putText(key, value));
	}

	/**
	 * Writes a value that expires.
	 *
	 * @param key the key
	 * @param value the value
	 * @param ttlSeconds how long the value lives, at least 60
	 */
	default void put(String key, String value, int ttlSeconds) {
		Async.awaitVoid(putText(key, value, Options.expirationTtl(ttlSeconds)));
	}

	/**
	 * Writes a value with metadata, which {@link #list()} returns without a second read.
	 *
	 * @param key the key
	 * @param value the value
	 * @param metadata the metadata, at most 1024 bytes once serialised
	 */
	default void putWithMetadata(String key, String value, TSObject metadata) {
		Async.awaitVoid(putText(key, value, Options.metadata(metadata)));
	}

	/**
	 * Writes bytes.
	 *
	 * @param key the key
	 * @param value the bytes
	 */
	default void putBytes(String key, ArrayBuffer value) {
		Async.awaitVoid(putBuffer(key, value));
	}

	/**
	 * Writes bytes.
	 *
	 * @param key the key
	 * @param value the bytes
	 */
	default void putBytes(String key, byte[] value) {
		putBytes(key, Bytes.toBuffer(value));
	}

	/**
	 * Removes a key. Succeeds whether or not it was present.
	 *
	 * @param key the key
	 */
	default void delete(String key) {
		Async.awaitVoid(remove(key));
	}

	/** {@return the first page of keys} */
	default Listing list() {
		return Async.await(listKeys(null));
	}

	/**
	 * Lists keys under a prefix.
	 *
	 * @param prefix the prefix
	 * @return the first page of matching keys
	 */
	default Listing list(String prefix) {
		return Async.await(listKeys(Options.prefix(prefix)));
	}

	/**
	 * Lists keys from where a previous page left off.
	 *
	 * @param prefix the prefix, or {@code null} for all keys
	 * @param cursor {@link Listing#getCursor()} from the previous page
	 * @param limit how many keys to return, at most 1000
	 * @return the next page
	 */
	default Listing list(String prefix, String cursor, int limit) {
		return Async.await(listKeys(Options.listing(prefix, cursor, limit)));
	}

	/** {@return every key under a prefix, following the cursor until the listing completes} */
	default List<String> listAll(String prefix) {
		List<String> names = new ArrayList<>();
		String cursor = null;
		while (true) {
			Listing page = Async.await(listKeys(Options.listing(prefix, cursor, 1000)));
			JSArrayReader<Key> keys = page.getKeys();
			for (int i = 0; i < keys.getLength(); i++) names.add(keys.get(i).getName());
			if (page.isListComplete()) return names;
			cursor = page.getCursor();
		}
	}

	@JSMethod("get")
	JSPromise<JSString> getText(String key);

	@JSMethod("get")
	JSPromise<TSObject> getWithType(String key, String type);

	// the type argument is not optional here; without it the platform answers text and the buffer
	// this is declared to return is a string
	@JSMethod("get")
	JSPromise<ArrayBuffer> getBuffer(String key, String type);

	@JSMethod("getWithMetadata")
	JSPromise<Entry> getMetadata(String key, String type);

	@JSMethod("put")
	JSPromise<JSObject> putText(String key, String value);

	@JSMethod("put")
	JSPromise<JSObject> putText(String key, String value, JSObject options);

	@JSMethod("put")
	JSPromise<JSObject> putBuffer(String key, ArrayBuffer value);

	@JSMethod("delete")
	JSPromise<JSObject> remove(String key);

	@JSMethod("list")
	JSPromise<Listing> listKeys(JSObject options);

	/** A value and the metadata stored with it. */
	interface Entry extends JSObject {
		/** {@return the value, or {@code null} when the key is absent} */
		@JSProperty
		JSString getValue();

		/** {@return the metadata, or {@code null} when none was stored} */
		@JSProperty
		TSObject getMetadata();

		/** {@return the value as a Java string, or {@code null}} */
		default String value() {
			JSString value = getValue();
			return value == null ? null : value.stringValue();
		}
	}

	/** One page of a key listing. */
	interface Listing extends JSObject {
		/** {@return the keys in this page} */
		@JSProperty
		JSArrayReader<Key> getKeys();

		/** {@return whether this page is the last} */
		@JSProperty("list_complete")
		boolean isListComplete();

		/** {@return where the next page starts, or {@code null} when this one is the last} */
		@JSProperty
		String getCursor();

		/** {@return the key names in this page} */
		default List<String> names() {
			JSArrayReader<Key> keys = getKeys();
			List<String> names = new ArrayList<>(keys.getLength());
			for (int i = 0; i < keys.getLength(); i++) names.add(keys.get(i).getName());
			return names;
		}
	}

	/** One key in a listing. */
	interface Key extends JSObject {
		/** {@return the key name} */
		@JSProperty
		String getName();

		/** {@return when the key expires, in seconds since the epoch, or 0 when it does not} */
		@JSProperty
		int getExpiration();

		/** {@return the metadata stored with the value, or {@code null}} */
		@JSProperty
		TSObject getMetadata();
	}
}
