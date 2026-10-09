import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

async function lines(path: string): Promise<string[]> {
	const response = await env.CORE.fetch(`https://bytebox.test${path}`);
	const text = await response.text();
	if (!response.ok) throw new Error(`${path} answered ${response.status}: ${text}`);
	return text.split('\n');
}

describe('blocking on a lock or a future from Java', () => {
	it('lets the handler constructor block, because the entry builds it on a fiber', async () => {
		expect(await lines('/startuplock')).toEqual([
			'first holds',
			'second asks',
			'first releases',
			'second holds',
			'second released',
			'held false'
		]);
	});

	it('holds a second fiber in lock() until the first releases', async () => {
		expect(await lines('/lockcontend')).toEqual([
			'first holds',
			'second asks',
			'first releases',
			'second holds',
			'second released',
			'held false'
		]);
	});

	it('hands a condition signal across fibers and times waits out', async () => {
		expect(await lines('/lockcondition')).toEqual([
			'waiter waits',
			'signaller signals',
			'waiter wakes',
			'timed await false',
			'tryLock short false',
			'tryLock long true'
		]);
	});

	it('blocks get() on a future another fiber completes, and reports failures as the JDK does', async () => {
		expect(await lines('/lockfuture')).toEqual([
			'chain abcd',
			'pending late',
			'never TimeoutException',
			'join boom',
			'get IllegalStateException',
			'allOf 3'
		]);
	});
});
