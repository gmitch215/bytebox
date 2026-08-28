package dev.gmitch215.bytebox.net.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The headers of a request or a response, standing in for {@code java.net.http.HttpHeaders}.
 *
 * @since 1.0.0
 */
public final class HttpHeaders {

	private final Map<String, List<String>> values;

	private HttpHeaders(Map<String, List<String>> values) {
		this.values = values;
	}

	/**
	 * Headers from a map.
	 *
	 * @param values name to values, keyed however the caller had them
	 * @return the headers, keyed in lower case because HTTP does not distinguish
	 */
	public static HttpHeaders of(Map<String, List<String>> values) {
		Map<String, List<String>> lowered = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : values.entrySet()) {
			lowered
				.computeIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), any -> new ArrayList<>())
				.addAll(entry.getValue());
		}
		return new HttpHeaders(lowered);
	}

	/** {@return every header, keyed in lower case} */
	public Map<String, List<String>> map() {
		return Collections.unmodifiableMap(values);
	}

	/**
	 * The last value of a header.
	 *
	 * @param name the header name, matched without regard to case
	 * @return the value, empty when the header is absent
	 */
	public Optional<String> firstValue(String name) {
		List<String> found = allValues(name);
		return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
	}

	/**
	 * The value of a header read as a number.
	 *
	 * @param name the header name
	 * @return the number, empty when the header is absent or is not one
	 */
	public OptionalLong firstValueAsLong(String name) {
		Optional<String> value = firstValue(name);
		if (value.isEmpty()) return OptionalLong.empty();
		try {
			return OptionalLong.of(Long.parseLong(value.get().trim()));
		} catch (NumberFormatException notANumber) {
			return OptionalLong.empty();
		}
	}

	/**
	 * Every value of a header.
	 *
	 * @param name the header name, matched without regard to case
	 * @return the values, empty when the header is absent
	 */
	public List<String> allValues(String name) {
		List<String> found = values.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
		return found == null ? List.of() : Collections.unmodifiableList(found);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof HttpHeaders && values.equals(((HttpHeaders) other).values);
	}

	@Override
	public int hashCode() {
		return values.hashCode();
	}

	@Override
	public String toString() {
		return "HttpHeaders " + values;
	}
}
