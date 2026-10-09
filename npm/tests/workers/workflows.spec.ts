import { env } from 'cloudflare:test';
import { beforeAll, describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

interface Described {
	created: string[];
	errors: { index: number; id: string | null; code: number; message: string }[];
}

async function route<T>(path: string): Promise<T> {
	const response = await env.CORE.fetch(`https://bytebox.test${path}`);
	if (!response.ok) {
		throw new Error(`${path} answered ${response.status}: ${await response.text()}`);
	}
	return (await response.json()) as T;
}

const unique = () => `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;

describe('a Java Workflow on the local engine', () => {
	it('runs both steps and the sleep, and returns what run returned', async () => {
		const id = `run-${unique()}`;
		const result = await route<Record<string, any>>(`/workflowrun?id=${id}`);

		expect(result.id).toBe(id);
		expect(result.status).toBe('complete');
		expect(result.output.greeting).toBe('hello ada');
		expect(result.output.shout).toBe(`HELLO ADA from ${id}`);
		expect(result.output.instance).toBe(id);
		expect(result.output.started).toBe(true);
	}, 30_000);

	it('builds the Workflow on a fiber, so its constructor can lock and join', async () => {
		const result = await route<Record<string, any>>(`/workflowrun?id=built-${unique()}`);

		expect(result.status).toBe('complete');
		expect(result.output.built).toBe(
			'first holds|second asks|first releases|second holds|second released|held false'
		);
	}, 30_000);

	it('retries a step whose body threw, from the config it was given', async () => {
		const id = `flaky-${unique()}`;
		const result = await route<Record<string, any>>(`/workflowrun?id=${id}&flaky=true`);

		expect(result.status).toBe('complete');
		expect(result.output.flaky).toBe('succeeded on attempt 2');
	}, 30_000);

	it('starts a batch from the list form and runs each instance', async () => {
		const id = `batch-${unique()}`;
		const result = await route<{ created: string[]; finished: string }>(
			`/workflowbatch?id=${id}`
		);

		expect(result.created).toEqual([`${id}-1`, `${id}-2`]);
		expect(result.finished).toBe('complete');
	}, 30_000);
});

describe('createBatch with the options form on the local engine', () => {
	interface Engine {
		count: Described;
		countStatuses: string[];
		instances: Described;
		instanceStatuses: string[];
		again: Described;
		mixed: Described;
		mixedStatuses: string[];
	}

	const id = `engine-${unique()}`;
	let engine: Engine;

	beforeAll(async () => {
		engine = await route<Engine>(`/workflowengine?id=${id}`);
	}, 90_000);

	it('starts a count of instances with shared params and runs each', () => {
		expect(engine.count.created).toHaveLength(2);
		expect(new Set(engine.count.created).size).toBe(2);
		expect(engine.count.errors).toEqual([]);
		expect(engine.countStatuses).toEqual(['complete', 'complete']);
	});

	it('starts the instances it is given, under the ids it is given', () => {
		expect(engine.instances.created).toEqual([`${id}-a`, `${id}-b`]);
		expect(engine.instances.errors).toEqual([]);
		expect(engine.instanceStatuses).toEqual(['complete', 'complete']);
	});

	it('reports each instance that already exists, by index and id', () => {
		expect(engine.again.created).toEqual([]);
		expect(engine.again.errors).toEqual([
			{
				index: 0,
				id: `${id}-a`,
				code: 10405,
				message: 'workflows.api.error.instance.already_exists'
			},
			{
				index: 1,
				id: `${id}-b`,
				code: 10405,
				message: 'workflows.api.error.instance.already_exists'
			}
		]);
	});

	it('starts the rest of a batch when one instance in it already exists', () => {
		expect(engine.mixed.created).toEqual([`${id}-c`]);
		expect(engine.mixed.errors).toHaveLength(1);
		expect(engine.mixed.errors[0]?.id).toBe(`${id}-a`);
		expect(engine.mixed.errors[0]?.code).toBe(10405);
		expect(engine.mixedStatuses).toEqual(['complete']);
	});
});
