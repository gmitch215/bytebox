import { env } from 'cloudflare:test';

declare global {
	namespace Cloudflare {
		interface Env {
			FIXTURE: Fetcher;
		}
	}
}

/** Runs one route of the Worker under test and returns what it reported. */
export async function drive<T>(path: string): Promise<T> {
	const response = await env.FIXTURE.fetch(`https://bytebox.test${path}`);
	if (!response.ok) throw new Error(`${path} answered ${response.status}`);
	return (await response.json()) as T;
}
