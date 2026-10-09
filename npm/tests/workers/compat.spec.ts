import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
			CORE_COMPAT: Fetcher;
			COMPAT_PROBE: Fetcher;
		}
	}
}

async function route<T>(worker: Fetcher, path: string): Promise<T> {
	const response = await worker.fetch(`https://bytebox.test${path}`);
	if (!response.ok) {
		throw new Error(`${path} answered ${response.status}: ${await response.text()}`);
	}
	return (await response.json()) as T;
}

describe('a Java handler with nodejs_compat on', () => {
	it('runs under the flags it was configured with', async () => {
		const probe = await route<{ process: string; nodeVersion: string | null }>(
			env.COMPAT_PROBE,
			'/'
		);

		expect(probe.process).toBe('object');
		expect(probe.nodeVersion).toMatch(/^\d+\./);
	});

	it('gives the Java Worker itself the Node globals, and the default one none', async () => {
		const compat = await route<{ process: string; version: string | null }>(
			env.CORE_COMPAT,
			'/__node'
		);
		const plain = await route<{ process: string; version: string | null }>(env.CORE, '/__node');

		expect(compat.process).toBe('object');
		expect(compat.version).toMatch(/^\d+\./);
		expect(plain.process).toBe('undefined');
		expect(plain.version).toBeNull();
	});

	it('answers the same URL routes as without it', async () => {
		const result = await route<Record<string, string>>(env.CORE_COMPAT, '/url?team=core');

		expect(result.path).toBe('/url');
		expect(result.host).toBe('bytebox.test');
		expect(result.origin).toBe('https://bytebox.test');
		expect(result.team).toBe('core');
		expect(result.absent).toBe('null');
	});

	it('reads and writes KV', async () => {
		const result = await route<Record<string, string>>(env.CORE_COMPAT, '/kv');

		expect(result.read).toBe('hello from kv');
		expect(result.absent).toBe('null');
		expect(result.deleted).toBe('null');
		expect(result.keys).toBe('greeting');
	});

	it('runs fibers, joins them, and recovers from one that threw', async () => {
		const result = await route<Record<string, string>>(env.CORE_COMPAT, '/async');

		expect(result.first).toBe('first done');
		expect(result.second).toBe('second done');
		expect(result.recovered).toBe('fell back');
		expect(result.clockMoved).toBe('true');
	});

	it('round-trips a record through a JSON codec, long included', async () => {
		const result = await route<Record<string, string>>(env.CORE_COMPAT, '/json');

		expect(result.sku).toBe('A-1');
		expect(result.total).toBe('9007199254740993');
		expect(result.roundTrips).toBe('true');
		expect(JSON.parse(result.encoded!)).toMatchObject({
			sku: 'A-1',
			total: '9007199254740993'
		});
	});

	it('agrees with the default configuration on every route it drives', async () => {
		for (const path of [
			'/url?team=core',
			'/kv',
			'/async',
			'/json',
			'/builtin',
			'/collections'
		]) {
			expect(await route(env.CORE_COMPAT, path), path).toEqual(await route(env.CORE, path));
		}
	});
});
