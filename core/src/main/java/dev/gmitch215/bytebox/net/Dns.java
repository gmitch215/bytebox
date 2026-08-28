package dev.gmitch215.bytebox.net;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;

/**
 * The DNS-over-HTTPS answer, read on the JavaScript side.
 *
 * <p>Separate from {@link InetAddress} because that class is a substitute - the compiler renames it to
 * the one it stands in for - and a renamed class loses the annotations that turn a {@code native}
 * method into a call into JavaScript.
 */
final class Dns {

	private Dns() {}

	/** A request the resolver answers with JSON rather than with wire-format DNS. */
	@JSBody(script = "return { method: 'GET', headers: { accept: 'application/dns-json' } };")
	static native TSObject jsonRequest();

	/**
	 * The addresses in an answer, comma separated.
	 *
	 * <p>Record type 1 is A and 28 is AAAA; anything else in the answer is a CNAME on the way to one
	 * of them and carries a name rather than an address.
	 */
	@JSBody(
		params = "answer",
		script = "var found = [];" +
			"var records = answer && answer.Answer ? answer.Answer : [];" +
			"for (var i = 0; i < records.length; i++) {" +
			"  if (records[i].type === 1 || records[i].type === 28) found.push(records[i].data);" +
			"}" +
			"return found.join(',');"
	)
	static native String addresses(TSObject answer);
}
