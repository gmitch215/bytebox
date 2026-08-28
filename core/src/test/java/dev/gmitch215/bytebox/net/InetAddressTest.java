package dev.gmitch215.bytebox.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Everything {@code InetAddress} answers without asking a resolver.
 *
 * <p>Resolving a name is a request over the network, which this lane does not make. What it does check
 * is the half that decides whether a request is made at all - an address written as one, a loopback
 * name - and the classification and byte conversion, which is where an off-by-one hides.
 */
@DisplayName("InetAddress")
class InetAddressTest {

	@Test
	@DisplayName("does not resolve an address that is already written as one")
	void doesNotResolveAnAddress() throws Exception {
		InetAddress address = InetAddress.getByName("93.184.216.34");

		assertEquals("93.184.216.34", address.getHostAddress());
		assertEquals("93.184.216.34", address.getHostName());
		assertEquals("/93.184.216.34", address.toString());
	}

	@Test
	@DisplayName("does not resolve localhost, nor an empty name")
	void doesNotResolveLocalhost() throws Exception {
		assertEquals("127.0.0.1", InetAddress.getByName("localhost").getHostAddress());
		assertEquals("localhost", InetAddress.getByName("localhost").getHostName());
		assertEquals("127.0.0.1", InetAddress.getByName(null).getHostAddress());
		assertEquals("127.0.0.1", InetAddress.getByName("").getHostAddress());
	}

	@Test
	@DisplayName("strips the brackets an IPv6 address is written in")
	void stripsBrackets() throws Exception {
		assertEquals("::1", InetAddress.getByName("[::1]").getHostAddress());
	}

	@Test
	@DisplayName("builds an address from its bytes, and refuses a length that is not one")
	void buildsFromBytes() throws Exception {
		InetAddress four = InetAddress.getByAddress("here", new byte[] { 10, 0, 0, 1 });
		assertEquals("10.0.0.1", four.getHostAddress());
		assertEquals("here", four.getHostName());
		assertEquals("here/10.0.0.1", four.toString());

		byte[] sixteen = new byte[16];
		sixteen[15] = 1;
		assertEquals("0:0:0:0:0:0:0:1", InetAddress.getByAddress(sixteen).getHostAddress());

		assertThrows(UnknownHostException.class, () -> InetAddress.getByAddress(new byte[3]));
		assertThrows(UnknownHostException.class, () -> InetAddress.getByAddress(null));
	}

	@Test
	@DisplayName("reads an address back as the bytes it was built from")
	void readsBytesBack() throws Exception {
		byte[] four = { (byte) 192, (byte) 168, 1, (byte) 255 };
		assertArrayEquals(four, InetAddress.getByAddress(four).getAddress());
		assertArrayEquals(
			new byte[] { 127, 0, 0, 1 },
			InetAddress.getByName("127.0.0.1").getAddress()
		);
	}

	@ParameterizedTest
	@CsvSource({
		"127.0.0.1, true, false, false, false",
		"127.1.2.3, true, false, false, false",
		"::1, true, false, false, false",
		"10.1.2.3, false, true, false, false",
		"192.168.0.1, false, true, false, false",
		"172.16.0.1, false, true, false, false",
		"172.31.255.255, false, true, false, false",
		"172.15.0.1, false, false, false, false",
		"172.32.0.1, false, false, false, false",
		"224.0.0.1, false, false, true, false",
		"239.255.255.255, false, false, true, false",
		"240.0.0.1, false, false, false, false",
		"ff02:0:0:0:0:0:0:1, false, false, true, false",
		"0.0.0.0, false, false, false, true",
		"93.184.216.34, false, false, false, false"
	})
	@DisplayName("classifies an address the way the ranges say")
	void classifies(
		String address,
		boolean loopback,
		boolean siteLocal,
		boolean multicast,
		boolean any
	) throws Exception {
		InetAddress parsed = InetAddress.getByName(address);

		assertEquals(loopback, parsed.isLoopbackAddress(), address + " loopback");
		assertEquals(siteLocal, parsed.isSiteLocalAddress(), address + " site local");
		assertEquals(multicast, parsed.isMulticastAddress(), address + " multicast");
		assertEquals(any, parsed.isAnyLocalAddress(), address + " unspecified");
	}

	@Test
	@DisplayName("is equal by address rather than by the name it came from")
	void isEqualByAddress() throws Exception {
		InetAddress named = InetAddress.getByAddress("one", new byte[] { 10, 0, 0, 1 });
		InetAddress other = InetAddress.getByAddress("two", new byte[] { 10, 0, 0, 1 });

		assertEquals(named, other);
		assertEquals(named.hashCode(), other.hashCode());
		assertFalse(named.equals(InetAddress.getByName("10.0.0.2")));
		assertFalse(named.equals("10.0.0.1"));
	}

	@Test
	@DisplayName("reports the same name for a canonical lookup, since it makes no reverse one")
	void reportsTheSameCanonicalName() throws Exception {
		InetAddress address = InetAddress.getByAddress("here", new byte[] { 10, 0, 0, 1 });
		assertEquals("here", address.getCanonicalHostName());
	}

	/**
	 * The resolver is a request over the network, which this lane cannot make, so reaching it fails
	 * here rather than answering. That failure is what says the input was treated as a name.
	 */
	@Test
	@DisplayName("treats a name that is not four numbers as a name to resolve")
	void treatsANameAsAName() {
		assertThrows(Throwable.class, () -> InetAddress.getByName("example.com"));
		assertThrows(Throwable.class, () -> InetAddress.getByName("1.2.3"));
		assertThrows(Throwable.class, () -> InetAddress.getByName("1.2.3.4.5"));
		assertThrows(Throwable.class, () -> InetAddress.getByName("256.1.1.1"));
		assertThrows(Throwable.class, () -> InetAddress.getByName("1.2.3.a"));
		assertThrows(Throwable.class, () -> InetAddress.getByName("1.2.3.1234"));
	}

	/**
	 * Anything with a colon in it is taken for an address, and the sixteen bytes were written from the
	 * groups without checking how many there were - so a run of colons ran off the end of the array.
	 */
	@Test
	@DisplayName("refuses an address that is all colons rather than writing past sixteen bytes")
	void refusesARunOfColons() {
		for (String malformed : new String[] {
			"::::::::::::::::::",
			"1:2:3:4:5:6:7:8:9",
			"1:2:3:4:5:6:7:8:9:a:b:c:d:e:f:0:1",
			"12345::1"
		}) {
			assertThrows(
				RuntimeException.class,
				() -> InetAddress.getByName(malformed).getAddress(),
				malformed
			);
		}
	}

	@Test
	@DisplayName("a well-formed address still reads back as sixteen bytes")
	void aWellFormedAddressStillReads() throws Exception {
		assertEquals(16, InetAddress.getByName("1:2:3:4:5:6:7:8").getAddress().length);
		assertEquals(16, InetAddress.getByName("::1").getAddress().length);
		assertEquals(16, InetAddress.getByName("ff02::1").getAddress().length);
	}

	/**
	 * A name is whatever a request asked for, so remembering every one that resolved is a way to grow
	 * an isolate's heap from outside it. The map is bounded and emptied when it fills; what is checked
	 * here is that a lookup which needs no resolver is not remembered at all.
	 */
	@Test
	@DisplayName(
		"an address that needs no lookup is not remembered, so a stream of them costs nothing"
	)
	void anAddressIsNotRemembered() throws Exception {
		int before = remembered();
		for (int index = 0; index < 2000; index++) {
			InetAddress.getByName("10.0." + index / 256 + "." + (index % 256));
		}
		assertEquals(before, remembered());
	}

	private static int remembered() throws Exception {
		java.lang.reflect.Field field = InetAddress.class.getDeclaredField("RESOLVED");
		field.setAccessible(true);
		return ((java.util.Map<?, ?>) field.get(null)).size();
	}

	@Test
	@DisplayName("carries the message a failed lookup names")
	void carriesTheMessage() {
		assertEquals("nothing.invalid", new UnknownHostException("nothing.invalid").getMessage());
		assertTrue(new UnknownHostException().getMessage() == null);
		assertEquals("refused", new ConnectException("refused").getMessage());
		assertEquals("broken", new SocketException("broken").getMessage());
		assertEquals("late", new SocketTimeoutException("late").getMessage());
		assertTrue(new ConnectException() instanceof SocketException);
		assertTrue(new SocketException() instanceof java.io.IOException);
		assertTrue(new SocketTimeoutException() instanceof java.io.InterruptedIOException);
	}
}
