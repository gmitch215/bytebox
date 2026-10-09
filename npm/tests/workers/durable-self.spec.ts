import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			SELF: Fetcher;
		}
	}
}

async function answer(path: string, ms = 5_000): Promise<string> {
	const timeout = new Promise<never>((_, reject) =>
		setTimeout(() => reject(new Error(`${path} did not answer in ${ms} ms`)), ms)
	);
	const response = await Promise.race([env.SELF.fetch(`https://bytebox.test${path}`), timeout]);
	return await response.text();
}

describe('a Worker that calls a Durable Object it hosts itself', () => {
	it('answers, because the Durable Object has a gate of its own', async () => {
		const text = await answer('/durablebuilt?id=self-call');

		expect(text.split('\n')[0]).toBe('first holds');
	}, 15_000);

	// known limit: every Durable Object class shares one gate, so a call that holds it while it waits
	// on another Durable Object queues behind itself (this goes red when that is fixed)
	it.fails(
		'answers a Durable Object that waits on another one of the same Worker',
		async () => {
			const text = await answer('/relay', 1_500);

			expect(text.split('\n')[0]).toBe('first holds');
		},
		15_000
	);
});
