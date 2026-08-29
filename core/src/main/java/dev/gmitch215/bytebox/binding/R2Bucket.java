package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.js.JSArrays;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * An R2 bucket. Declared with {@code r2()}, named {@code BLOB} by default.
 *
 * <p>Strongly consistent, unlike KV: a read after a write returns the value that was written.
 *
 * {@snippet lang = "java":
 * R2Bucket blob = env.r2();
 * blob.put("greeting.txt", "hello world!");
 * String greeting = blob.getText("greeting.txt");
 *}
 *
 * @since 1.0.0
 */
public interface R2Bucket extends JSObject {
	/**
	 * Reads an object.
	 *
	 * @param key the key
	 * @return the object, or {@code null} when the key is absent
	 */
	default R2ObjectBody get(String key) {
		return Async.await(getObject(key));
	}

	/**
	 * Reads an object's body as text.
	 *
	 * @param key the key
	 * @return the body, or {@code null} when the key is absent
	 */
	default String getText(String key) {
		R2ObjectBody body = get(key);
		return body == null ? null : body.text();
	}

	/**
	 * Reads an object's body as bytes.
	 *
	 * @param key the key
	 * @return the body, or {@code null} when the key is absent
	 */
	default ArrayBuffer getBytes(String key) {
		R2ObjectBody body = get(key);
		return body == null ? null : body.bytes();
	}

	/**
	 * Reads part of an object.
	 *
	 * @param key the key
	 * @param offset where to start
	 * @param length how many bytes to read
	 * @return the object, or {@code null} when the key is absent
	 */
	default R2ObjectBody getRange(String key, int offset, int length) {
		return Async.await(getObject(key, Options.r2Get(null, Options.range(offset, length))));
	}

	/**
	 * Reads an object's metadata without its body.
	 *
	 * @param key the key
	 * @return the metadata, or {@code null} when the key is absent
	 */
	default R2Object head(String key) {
		return Async.await(headObject(key));
	}

	/**
	 * Writes text.
	 *
	 * @param key the key
	 * @param value the body
	 * @return what was written
	 */
	default R2Object put(String key, String value) {
		return Async.await(putText(key, value));
	}

	/**
	 * Writes text with a content type.
	 *
	 * @param key the key
	 * @param value the body
	 * @param contentType the media type to serve it as
	 * @return what was written
	 */
	default R2Object put(String key, String value, String contentType) {
		return Async.await(putText(key, value, Options.r2Put(contentType, null)));
	}

	/**
	 * Writes bytes.
	 *
	 * @param key the key
	 * @param value the body
	 * @return what was written
	 */
	default R2Object putBytes(String key, ArrayBuffer value) {
		return Async.await(putBuffer(key, value));
	}

	/**
	 * Writes bytes.
	 *
	 * @param key the key
	 * @param value the body
	 * @return what was written
	 */
	default R2Object putBytes(String key, byte[] value) {
		return putBytes(key, Bytes.toBuffer(value));
	}

	/**
	 * Writes bytes with a content type and custom metadata.
	 *
	 * @param key the key
	 * @param value the body
	 * @param contentType the media type, or {@code null}
	 * @param metadata custom metadata, or {@code null}
	 * @return what was written
	 */
	default R2Object putBytes(
		String key,
		ArrayBuffer value,
		String contentType,
		TSObject metadata
	) {
		return Async.await(putBuffer(key, value, Options.r2Put(contentType, metadata)));
	}

	/**
	 * Removes an object. Succeeds whether or not it was present.
	 *
	 * @param key the key
	 */
	default void delete(String key) {
		Async.awaitVoid(deleteObject(key));
	}

	/**
	 * Removes several objects in one call.
	 *
	 * @param keys the keys
	 */
	default void delete(String... keys) {
		JSString[] names = new JSString[keys.length];
		for (int i = 0; i < keys.length; i++) names[i] = JSString.valueOf(keys[i]);
		Async.awaitVoid(deleteObjects(JSArrays.of(names)));
	}

	/** {@return the first page of objects} */
	default R2Objects list() {
		return Async.await(listObjects(null));
	}

	/**
	 * Lists objects under a prefix.
	 *
	 * @param prefix the prefix
	 * @return the first page of matching objects
	 */
	default R2Objects list(String prefix) {
		return Async.await(listObjects(Options.prefix(prefix)));
	}

	/**
	 * Lists objects from where a previous page left off.
	 *
	 * @param prefix the prefix, or {@code null} for everything
	 * @param cursor {@link R2Objects#getCursor()} from the previous page
	 * @param limit how many to return, at most 1000
	 * @return the next page
	 */
	default R2Objects list(String prefix, String cursor, int limit) {
		return Async.await(listObjects(Options.r2List(prefix, cursor, limit, null)));
	}

	/** {@return every key under a prefix, following the cursor until the listing completes} */
	default List<String> listAll(String prefix) {
		List<String> keys = new ArrayList<>();
		String cursor = null;
		while (true) {
			R2Objects page = Async.await(listObjects(Options.r2List(prefix, cursor, 1000, null)));
			JSArrayReader<R2Object> objects = page.getObjects();
			for (int i = 0; i < objects.getLength(); i++) keys.add(objects.get(i).getKey());
			if (!page.isTruncated()) return keys;
			cursor = page.getCursor();
		}
	}

	@JSMethod("get")
	JSPromise<R2ObjectBody> getObject(String key);

	@JSMethod("get")
	JSPromise<R2ObjectBody> getObject(String key, JSObject options);

	@JSMethod("head")
	JSPromise<R2Object> headObject(String key);

	@JSMethod("put")
	JSPromise<R2Object> putText(String key, String value);

	@JSMethod("put")
	JSPromise<R2Object> putText(String key, String value, JSObject options);

	@JSMethod("put")
	JSPromise<R2Object> putBuffer(String key, ArrayBuffer value);

	@JSMethod("put")
	JSPromise<R2Object> putBuffer(String key, ArrayBuffer value, JSObject options);

	@JSMethod("delete")
	JSPromise<JSObject> deleteObject(String key);

	@JSMethod("delete")
	JSPromise<JSObject> deleteObjects(JSArray<JSString> keys);

	@JSMethod("list")
	JSPromise<R2Objects> listObjects(JSObject options);

	/**
	 * An object's metadata.
	 *
	 * @since 1.0.0
	 */
	interface R2Object extends JSObject {
		/** {@return the key} */
		@JSProperty
		String getKey();

		/** {@return the size in bytes} */
		@JSProperty
		double getSize();

		/** {@return the entity tag} */
		@JSProperty
		String getEtag();

		/** {@return the version this write created} */
		@JSProperty
		String getVersion();

		/** {@return the HTTP metadata stored with the object} */
		@JSProperty
		TSObject getHttpMetadata();

		/** {@return the custom metadata stored with the object} */
		@JSProperty
		TSObject getCustomMetadata();
	}

	/**
	 * An object's metadata together with its body.
	 *
	 * <p>A body can be read once. Reading it twice raises, because it is a stream.
	 *
	 * @since 1.0.0
	 */
	interface R2ObjectBody extends R2Object {
		/** {@return the body, decoded as UTF-8} */
		default String text() {
			return Async.await(readText()).stringValue();
		}

		/** {@return the body, as bytes} */
		default ArrayBuffer bytes() {
			return Async.await(readBytes());
		}

		/** {@return the body, parsed as JSON} */
		default TSObject json() {
			return Async.await(readJson());
		}

		@JSMethod("text")
		JSPromise<JSString> readText();

		@JSMethod("arrayBuffer")
		JSPromise<ArrayBuffer> readBytes();

		@JSMethod("json")
		JSPromise<TSObject> readJson();
	}

	/**
	 * One page of an object listing.
	 *
	 * @since 1.0.0
	 */
	interface R2Objects extends JSObject {
		/** {@return the objects in this page} */
		@JSProperty
		JSArrayReader<R2Object> getObjects();

		/** {@return whether more pages follow} */
		@JSProperty
		boolean isTruncated();

		/** {@return where the next page starts, or {@code null} when this one is the last} */
		@JSProperty
		String getCursor();

		/** {@return the keys in this page} */
		default List<String> keys() {
			JSArrayReader<R2Object> objects = getObjects();
			List<String> keys = new ArrayList<>(objects.getLength());
			for (int i = 0; i < objects.getLength(); i++) keys.add(objects.get(i).getKey());
			return keys;
		}
	}
}
