package fixture;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

public class Argv {
	@JSBody(
		params = "path",
		imports = @JSBodyImport(alias = "fs", fromModule = "bytebox:fs"),
		script = "return fs.readText(path);"
	)
	private static native String readText(String path);

	public static void main(String[] args) {
		System.out.println("argv:" + String.join(",", args));
		if (args.length == 0) return;
		System.out.println("read:" + readText(args[args.length - 1]));
	}
}
