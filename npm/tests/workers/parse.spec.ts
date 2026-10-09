import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';
import table from '../../../core/src/test/resources/dev/gmitch215/bytebox/text/parse-grammar.tsv?raw';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

async function values(): Promise<Record<string, string>> {
	const response = await env.CORE.fetch('https://bytebox.test/parse');
	if (!response.ok) {
		throw new Error(`/parse answered ${response.status}: ${await response.text()}`);
	}
	return (await response.json()) as Record<string, string>;
}

/** The strings the grammar table was written for and what a JVM answered for each. */
const rows = table
	.split('\n')
	.filter((line) => line.length > 0)
	.map((line) => {
		const [text, double, float] = line.split('\t').map(unescape);
		return { text, expected: `${double}\t${float}` };
	});

function unescape(text: string): string {
	return text.replace(/\\(u([0-9a-f]{4})|.)/g, (_, rest: string, code?: string) => {
		if (code !== undefined) return String.fromCharCode(parseInt(code, 16));
		if (rest === 't') return '\t';
		if (rest === 'n') return '\n';
		return rest;
	});
}

describe('Double and Float text on workerd', () => {
	it('prints and reads the values that used to differ from a JVM', async () => {
		const result = await values();

		expect(result.toString1e23).toBe('1.0E23');
		expect(result.toStringMin).toBe('4.9E-324');
		expect(result.bits1e23).toBe('44b52d02c7e14af6');
		expect(result.parseMin).toBe('true');
		expect(result.roundTrip).toBe('true');
		expect(result.float).toBe('3f800002');
	});

	it('accepts and refuses the strings a JVM does, with its messages, and reads the same values', async () => {
		const response = await env.CORE.fetch('https://bytebox.test/parsetable', {
			method: 'POST',
			body: JSON.stringify(rows.map((row) => row.text))
		});
		expect(response.ok).toBe(true);
		const answers = (await response.json()) as string[];

		expect(answers).toHaveLength(rows.length);
		const wrong = rows
			.map((row, at) => ({ text: row.text, expected: row.expected, got: answers[at] }))
			.filter((row) => row.got !== row.expected);
		expect(wrong.length, JSON.stringify(wrong.slice(0, 5))).toBe(0);
	});
});
