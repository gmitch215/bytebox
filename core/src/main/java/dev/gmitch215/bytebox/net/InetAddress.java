package dev.gmitch215.bytebox.net;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.js.TSObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An IP address, standing in for {@code java.net.InetAddress}.
 *
 * <p>The platform exposes no resolver, which is a statement about the API rather than about DNS: DNS
 * is a protocol, and one of the ways to speak it is over HTTPS. So a name is resolved by asking a
 * DNS-over-HTTPS resolver with {@code fetch}, which suspends the fiber the way any other lookup does
 * and reads like an ordinary call.
 *
 * <p>{@link #getByName} does not go over the network for an address written as one, nor for
 * {@code localhost}, so the common cases cost nothing. A resolved name is remembered for the life of
 * the isolate; the platform's own resolver caching is what decides how stale that can be, and a Worker
 * lives far too short a time for a TTL to be worth tracking.
 *
 * <p>Two limits. {@link #isReachable} sends an HTTPS request rather than an ICMP echo, because the
 * platform cannot send a datagram - so it reports whether a host answers HTTPS, which is a different
 * question and is documented as such rather than faked. And a lookup is a subrequest, so it counts
 * against the 50 the free plan allows per invocation.
 *
 * <p>What is not here, because nothing can back it: the local host's own address, a network interface,
 * and a reverse lookup of a name from an address the resolver was not asked for.
 *
 * @since 1.0.0
 */
public class InetAddress {

	/** Cloudflare's DNS-over-HTTPS endpoint, which {@code fetch} may reach even though TCP may not. */
	private static final String RESOLVER = "https://cloudflare-dns.com/dns-query";

	/**
	 * How many resolved names to remember.
	 *
	 * <p>Bounded, and emptied rather than trimmed when it fills. A name is whatever a request asked for,
	 * so an unbounded map is a way to grow an isolate's heap from outside it, and an isolate has 128 MB
	 * and a short life. Emptying is enough: the cost of a refill is one lookup.
	 */
	private static final int REMEMBERED = 256;

	private static final Map<String, List<InetAddress>> RESOLVED = new HashMap<>();

	private final String host;
	private final String address;

	InetAddress(String host, String address) {
		this.host = host;
		this.address = address;
	}

	/**
	 * The address of a host.
	 *
	 * @param host a host name, or an address written as one
	 * @return the first address the resolver gave
	 * @throws UnknownHostException when the resolver has no address for it
	 */
	public static InetAddress getByName(String host) throws UnknownHostException {
		return getAllByName(host)[0];
	}

	/**
	 * Every address of a host.
	 *
	 * @param host a host name, or an address written as one
	 * @return the addresses, IPv4 before IPv6
	 * @throws UnknownHostException when the resolver has no address for it
	 */
	public static InetAddress[] getAllByName(String host) throws UnknownHostException {
		if (host == null || host.isEmpty()) {
			return new InetAddress[] { new InetAddress("localhost", "127.0.0.1") };
		}
		String name =
			host.startsWith("[") && host.endsWith("]")
				? host.substring(1, host.length() - 1)
				: host;
		if (name.equals("localhost")) {
			return new InetAddress[] { new InetAddress(name, "127.0.0.1") };
		}
		if (isNumeric(name)) {
			return new InetAddress[] { new InetAddress(null, name) };
		}

		List<InetAddress> known = RESOLVED.get(name);
		if (known == null) {
			known = resolve(name);
			if (RESOLVED.size() >= REMEMBERED) RESOLVED.clear();
			RESOLVED.put(name, known);
		}
		if (known.isEmpty()) throw new UnknownHostException(name);
		return known.toArray(new InetAddress[0]);
	}

	/**
	 * An address for a host name, without asking a resolver.
	 *
	 * @param host the name to report, or null
	 * @param address the four or sixteen bytes of the address
	 * @return the address
	 * @throws UnknownHostException when the length is neither four nor sixteen
	 */
	public static InetAddress getByAddress(String host, byte[] address)
		throws UnknownHostException {
		if (address == null || (address.length != 4 && address.length != 16)) {
			throw new UnknownHostException("an address is four or sixteen bytes");
		}
		return new InetAddress(host, format(address));
	}

	/**
	 * An address, without asking a resolver.
	 *
	 * @param address the four or sixteen bytes of the address
	 * @return the address
	 * @throws UnknownHostException when the length is neither four nor sixteen
	 */
	public static InetAddress getByAddress(byte[] address) throws UnknownHostException {
		return getByAddress(null, address);
	}

	/** {@return the address as text} */
	public String getHostAddress() {
		return address;
	}

	/** {@return the name this address was resolved from, or the address when there was none} */
	public String getHostName() {
		return host == null ? address : host;
	}

	/** {@return the same as {@link #getHostName()}, since no reverse lookup is made} */
	public String getCanonicalHostName() {
		return getHostName();
	}

	/** {@return the four or sixteen bytes of the address} */
	public byte[] getAddress() {
		if (address.indexOf(':') >= 0) return sixteenBytes();
		String[] parts = address.split("\\.");
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i++) bytes[i] = (byte) Integer.parseInt(parts[i]);
		return bytes;
	}

	/**
	 * Whether the host answers an HTTPS request within a deadline.
	 *
	 * <p>Not an ICMP echo, which the platform cannot send. A host that is up but serves no HTTPS reads
	 * as unreachable here.
	 *
	 * @param timeoutMillis how long to allow, or zero for no limit
	 * @return whether it answered
	 * @throws IOException when the request could not be made at all
	 */
	public boolean isReachable(int timeoutMillis) throws IOException {
		try {
			TSObject init = Urls.init("HEAD", null, timeoutMillis, true);
			return Bytebox.fetch("https://" + getHostName() + "/", init).getStatus() > 0;
		} catch (RuntimeException refused) {
			return false;
		}
	}

	/** {@return whether this is a loopback address} */
	public boolean isLoopbackAddress() {
		return address.startsWith("127.") || address.equals("::1");
	}

	/** {@return whether this is an address only reachable inside a private network} */
	public boolean isSiteLocalAddress() {
		if (address.startsWith("10.") || address.startsWith("192.168.")) return true;
		if (!address.startsWith("172.")) return false;
		int second = Integer.parseInt(address.split("\\.")[1]);
		return second >= 16 && second <= 31;
	}

	/** {@return whether this is a multicast address} */
	public boolean isMulticastAddress() {
		if (address.indexOf(':') >= 0) return address
			.toLowerCase(java.util.Locale.ROOT)
			.startsWith("ff");
		int first = Integer.parseInt(address.split("\\.")[0]);
		return first >= 224 && first <= 239;
	}

	/** {@return whether this is the unspecified address} */
	public boolean isAnyLocalAddress() {
		return address.equals("0.0.0.0") || address.equals("::");
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof InetAddress && address.equals(((InetAddress) other).address);
	}

	@Override
	public int hashCode() {
		return address.hashCode();
	}

	@Override
	public String toString() {
		return (host == null ? "" : host) + "/" + address;
	}

	/** Both record types, asked for together, since a resolver answers each in one round trip. */
	private static List<InetAddress> resolve(String name) throws UnknownHostException {
		List<InetAddress> found = new ArrayList<>();
		for (String type : new String[] { "A", "AAAA" }) {
			try {
				Response answer = Bytebox.fetch(
					RESOLVER + "?name=" + name + "&type=" + type,
					Dns.jsonRequest()
				);
				if (!answer.isOk()) continue;
				for (String address : Dns.addresses(answer.json()).split(",")) {
					if (!address.isEmpty()) found.add(new InetAddress(name, address));
				}
			} catch (RuntimeException failed) {
				throw new UnknownHostException(name + ": " + failed.getMessage());
			}
		}
		return found;
	}

	private static boolean isNumeric(String host) {
		if (host.indexOf(':') >= 0) return true;
		String[] parts = host.split("\\.", -1);
		if (parts.length != 4) return false;
		for (String part : parts) {
			if (part.isEmpty() || part.length() > 3) return false;
			for (int i = 0; i < part.length(); i++) {
				if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
			}
			if (Integer.parseInt(part) > 255) return false;
		}
		return true;
	}

	private static String format(byte[] bytes) {
		StringBuilder text = new StringBuilder();
		if (bytes.length == 4) {
			for (int i = 0; i < 4; i++) {
				if (i > 0) text.append('.');
				text.append(bytes[i] & 0xFF);
			}
			return text.toString();
		}
		for (int i = 0; i < 16; i += 2) {
			if (i > 0) text.append(':');
			text.append(Integer.toHexString(((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF)));
		}
		return text.toString();
	}

	/**
	 * The sixteen bytes, with {@code ::} standing for the run of zeros in the middle.
	 *
	 * <p>The groups on each side are written from their own end, which is what makes the gap the right
	 * size without counting it. A second gap, or more groups than an address holds, is refused rather
	 * than written past the end of the array.
	 */
	private byte[] sixteenBytes() {
		int gap = address.indexOf("::");
		if (gap >= 0 && address.indexOf("::", gap + 1) >= 0) {
			throw new IllegalStateException("not an address: " + address);
		}
		String[] head = groups(gap < 0 ? address : address.substring(0, gap));
		String[] tail = gap < 0 ? new String[0] : groups(address.substring(gap + 2));
		if (gap < 0 ? head.length != 8 : head.length + tail.length > 7) {
			throw new IllegalStateException("not an address: " + address);
		}

		byte[] bytes = new byte[16];
		write(head, bytes, 0);
		write(tail, bytes, 16 - 2 * tail.length);
		return bytes;
	}

	private static String[] groups(String part) {
		return part.isEmpty() ? new String[0] : part.split(":", -1);
	}

	private void write(String[] parts, byte[] bytes, int at) {
		for (String part : parts) {
			if (part.isEmpty() || part.length() > 4) {
				throw new IllegalStateException("not an address: " + address);
			}
			int value = Integer.parseInt(part, 16);
			bytes[at++] = (byte) (value >> 8);
			bytes[at++] = (byte) value;
		}
	}
}
