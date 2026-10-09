package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;

/** The routes that print and parse doubles and floats, kept apart from the rest of the fixture. */
final class ParseRoutes {

	private ParseRoutes() {}

	/** The values a JVM prints and reads for the cases that used to differ. */
	static Response values() {
		double rounded = Double.longBitsToDouble(0x1dcb2e81a65dee3fL);
		TSObject answer = TSObject.object();
		answer.set("toString1e23", TSObject.of(Double.toString(1e23)));
		answer.set("toStringMin", TSObject.of(Double.toString(Double.MIN_VALUE)));
		answer.set("bits1e23", TSObject.of(hex(Double.parseDouble("1e23"))));
		answer.set("parseMin", TSObject.of(String.valueOf(Double.parseDouble("4.9E-324") == Double.MIN_VALUE)));
		answer.set(
			"roundTrip",
			TSObject.of(String.valueOf(Double.parseDouble(Double.toString(rounded)) == rounded))
		);
		answer.set("float", TSObject.of(Integer.toHexString(Float.floatToRawIntBits(Float.parseFloat("1.00000018")))));
		return Bytebox.json(answer);
	}

	/** One answer per string in the request body, a JSON array: the bits parsed, or the message. */
	static Response table(Request request) {
		List<String> rows = new ArrayList<>();
		for (String text : request.json().asStringList()) {
			rows.add(ofDouble(text) + "\t" + ofFloat(text));
		}
		return Bytebox.json(TSObject.array(rows));
	}

	private static String ofDouble(String text) {
		try {
			return "D" + hex(Double.parseDouble(text));
		} catch (NumberFormatException e) {
			return "!" + e.getMessage();
		}
	}

	private static String ofFloat(String text) {
		try {
			String bits = Integer.toHexString(Float.floatToRawIntBits(Float.parseFloat(text)));
			return "F" + "0".repeat(8 - bits.length()) + bits;
		} catch (NumberFormatException e) {
			return "!" + e.getMessage();
		}
	}

	private static String hex(double value) {
		String bits = Long.toHexString(Double.doubleToRawLongBits(value));
		return "0".repeat(16 - bits.length()) + bits;
	}
}
