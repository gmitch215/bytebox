import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

let counter = 0;

/** A key no other test (or an earlier run of this one) has put anything under. */
function fresh(): string {
	return `https://cache.test/${Date.now()}-${counter++}`;
}

async function route<T>(path: string, params: Record<string, string>): Promise<T> {
	const query = new URLSearchParams(params).toString();
	const response = await env.CORE.fetch(`https://bytebox.test${path}?${query}`);
	if (!response.ok) {
		throw new Error(`${path} answered ${response.status}: ${await response.text()}`);
	}
	return (await response.json()) as T;
}

describe('the Cache API from Java', () => {
	it('misses, stores, hits with the body and a header, then deletes', async () => {
		const result = await route<Record<string, string>>('/cacheround', { key: fresh() });

		expect(result.before).toBe('null');
		expect(result.hit).toBe('hello from the cache');
		expect(result.header).toBe('42');
		expect(result.status).toBe('200');
		expect(result.deleted).toBe('true');
		expect(result.after).toBe('null');
		expect(result.deletedAgain).toBe('false');
	});

	it('looks an entry up by Request as well as by URL', async () => {
		const result = await route<Record<string, string>>('/cacherequest', { key: fresh() });

		expect(result.byRequest).toBe('stored by url');
		expect(result.deletedByRequest).toBe('true');
		expect(result.afterDelete).toBe('null');
	});

	it('keeps named caches apart from each other and from the default one', async () => {
		const result = await route<Record<string, string>>('/cachenames', { key: fresh() });

		expect(result.named).toBe('in x');
		expect(result.otherNamed).toBe('null');
		expect(result.default).toBe('null');
		expect(result.reopened).toBe('in x');
	});

	it('keeps a copy of a response the handler also returns', async () => {
		const result = await route<Record<string, string>>('/cachecopy', { key: fresh() });

		expect(result.returned).toBe('body to share');
		expect(result.cached).toBe('body to share');
	});

	it('refuses what the platform refuses', async () => {
		const result = await route<Record<string, string>>('/cacherefuse', { key: fresh() });

		expect(result.post).toContain('TypeError: Cannot cache response to non-GET request');
		expect(result.partial).toContain('TypeError: Cannot cache response to a range request');
		expect(result.varyStar).toContain("TypeError: Cannot cache response with 'Vary: *' header");
	});

	it('accepts a put it does not store, which is how no-store and Set-Cookie answer', async () => {
		const result = await route<Record<string, string>>('/cacherefuse', { key: fresh() });

		expect(result.noStoreOutcome).toBe('returned');
		expect(result.noStore).toBe('null');
		expect(result.setCookieOutcome).toBe('returned');
		expect(result.setCookie).toBe('null');
	});
});
