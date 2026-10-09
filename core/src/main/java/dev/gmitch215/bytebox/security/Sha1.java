package dev.gmitch215.bytebox.security;

/** FIPS 180-4, section 6.1. */
final class Sha1 extends Engine {

	private final int[] state = new int[5];
	private final int[] w = new int[80];

	Sha1() {
		super(64, 8);
		init();
	}

	@Override
	int digestLength() {
		return 20;
	}

	@Override
	boolean littleEndian() {
		return false;
	}

	@Override
	void init() {
		state[0] = 0x67452301;
		state[1] = 0xefcdab89;
		state[2] = 0x98badcfe;
		state[3] = 0x10325476;
		state[4] = 0xc3d2e1f0;
	}

	@Override
	void compress(byte[] data, int offset) {
		for (int i = 0; i < 16; i++) {
			int at = offset + 4 * i;
			w[i] =
				((data[at] & 0xff) << 24) |
				((data[at + 1] & 0xff) << 16) |
				((data[at + 2] & 0xff) << 8) |
				(data[at + 3] & 0xff);
		}
		for (int i = 16; i < 80; i++) {
			w[i] = Integer.rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
		}
		int a = state[0];
		int b = state[1];
		int c = state[2];
		int d = state[3];
		int e = state[4];
		for (int i = 0; i < 80; i++) {
			int f;
			int k;
			if (i < 20) {
				f = (b & c) | (~b & d);
				k = 0x5a827999;
			} else if (i < 40) {
				f = b ^ c ^ d;
				k = 0x6ed9eba1;
			} else if (i < 60) {
				f = (b & c) | (b & d) | (c & d);
				k = 0x8f1bbcdc;
			} else {
				f = b ^ c ^ d;
				k = 0xca62c1d6;
			}
			int next = Integer.rotateLeft(a, 5) + f + e + k + w[i];
			e = d;
			d = c;
			c = Integer.rotateLeft(b, 30);
			b = a;
			a = next;
		}
		state[0] += a;
		state[1] += b;
		state[2] += c;
		state[3] += d;
		state[4] += e;
	}

	@Override
	byte[] output() {
		byte[] out = new byte[20];
		for (int i = 0; i < 20; i++) out[i] = (byte) (state[i >> 2] >>> (24 - 8 * (i & 3)));
		return out;
	}
}
