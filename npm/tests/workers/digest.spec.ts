import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

async function digest(): Promise<Record<string, string>> {
	const response = await env.CORE.fetch('https://bytebox.test/digest');
	if (!response.ok) {
		throw new Error(`/digest answered ${response.status}: ${await response.text()}`);
	}
	return (await response.json()) as Record<string, string>;
}

describe('java.security.MessageDigest from Java', () => {
	it('hashes abc with each algorithm to the published vector', async () => {
		const result = await digest();

		expect(result['MD5']).toBe('900150983cd24fb0d6963f7d28e17f72');
		expect(result['SHA-1']).toBe('a9993e364706816aba3e25717850c26c9cd0d89d');
		expect(result['SHA-256']).toBe(
			'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
		);
		expect(result['SHA-384']).toBe(
			'cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7'
		);
		expect(result['SHA-512']).toBe(
			'ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f'
		);
	});

	it('agrees with the JVM on 130 bytes added in two calls across a block boundary', async () => {
		const result = await digest();

		expect(result['split MD5']).toBe('7c05c285d0263c40a0437421b387a2a1');
		expect(result['split SHA-1']).toBe('6981ed7d97ffca517d531cd3d1874b43e11f1b46');
		expect(result['split SHA-256']).toBe(
			'8d39b60b9c767c58975b270c1d6b13c9b4507e5aee7ad496a3528e4c7f880721'
		);
		expect(result['split SHA-384']).toBe(
			'd9b681ba08ec0d0598dd3a2a37f909d01a231d22da52216126534402a58a072db35fdae555b99159894bc823f9dacfe7'
		);
		expect(result['split SHA-512']).toBe(
			'90272b89212c81b9700897f611f13ac1d291c33a437000c1423336b4d962dd39ce23413160f023963e12f4ccf90d2762b31bfc6818ef865e8a7cbf918a94c1db'
		);
	});

	it('refuses an algorithm it does not carry with the JDK message', async () => {
		const result = await digest();

		expect(result['missing']).toBe('SHA-224 MessageDigest not available');
	});
});
