package fixture;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

public class Imported {
	@JSBody(
		params = "n",
		imports = @JSBodyImport(alias = "m", fromModule = "bytebox-test-module"),
		script = "return m.twice(n);"
	)
	private static native int twice(int n);

	public static void main(String[] args) {
		System.out.println("twice(21) = " + twice(21));
	}
}
