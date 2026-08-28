package dev.gmitch215.bytebox.net;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Points the reachable parts of {@code java.net} at the ones in this package.
 *
 * <p>Found through {@code META-INF/services}, which is how the compiler's own class library registers
 * its substitutions. A project compiles against the real classes from the JDK, and the references are
 * rewritten when the program is compiled to WebAssembly, so a library that opens a socket or makes a
 * request the ordinary way needs no change and no shim.
 *
 * <p>{@code URL}, {@code URLConnection} and {@code HttpURLConnection} exist in the class library and
 * are replaced rather than supplied: its versions are wired to {@code XMLHttpRequest}, which this
 * platform does not have, so they compile and then fail on the first request. {@code Socket},
 * {@code InetAddress} and the {@code java.net.http} client are absent from it and are supplied.
 *
 * <p>Only the classes named here are substituted. {@code ServerSocket} and {@code DatagramSocket} are
 * deliberately absent: the platform can accept no inbound connection and speaks no UDP, so a program
 * using either should fail while it is being built rather than throw once it is running.
 *
 * @since 1.0.0
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(
			named("java.net.Socket")
				.or(named("java.net.URL"))
				.or(named("java.net.URLConnection"))
				.or(named("java.net.HttpURLConnection"))
				.or(named("java.net.InetAddress"))
				.or(named("java.net.UnknownHostException"))
				.or(named("java.net.SocketException"))
				.or(named("java.net.ConnectException"))
				.or(named("java.net.SocketTimeoutException"))
		).replacePackage("java.net", "dev.gmitch215.bytebox.net");
		// named by pattern rather than by package, because a class in this package that is NOT
		// substituted is what holds the calls into JavaScript: a substituted class loses them
		sink.selectClasses(namePattern("java.net.http.Http*")).replacePackage(
			"java.net.http",
			"dev.gmitch215.bytebox.net.http"
		);
	}
}
