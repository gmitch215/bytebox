package fixture;

import java.nio.ByteBuffer;

public class Direct {
	public static void main(String[] args) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(64);
		buffer.putInt(0, 0x2a2a2a2a);
		System.out.println("direct:" + buffer.isDirect() + " read:" + Integer.toHexString(buffer.getInt(0)));
	}
}
