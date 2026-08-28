import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

/** Drives one route of the compiled Java worker and returns the JSON it answered with. */
async function route<T>(path: string): Promise<T> {
	const response = await env.CORE.fetch(`https://bytebox.test${path}`);
	if (!response.ok) {
		throw new Error(`${path} answered ${response.status}: ${await response.text()}`);
	}
	return (await response.json()) as T;
}

describe('a Java handler on workerd', () => {
	it('routes on the request path and answers a response it built', async () => {
		const result = await route<Record<string, string>>('/url?team=core');

		expect(result.path).toBe('/url');
		expect(result.host).toBe('bytebox.test');
		expect(result.origin).toBe('https://bytebox.test');
		expect(result.method).toBe('GET');
	});

	it('reads query parameters through the platform parser', async () => {
		const result = await route<Record<string, string>>('/url?team=core');

		expect(result.team).toBe('core');
		expect(result.absent).toBe('null');
		expect(result.fallback).toBe('none');
	});

	it('answers 404 for a path the handler does not route', async () => {
		const response = await env.CORE.fetch('https://bytebox.test/nowhere');

		expect(response.status).toBe(404);
		expect(await response.text()).toBe('no route for /nowhere');
	});

	it('fails the request on an uncaught Java exception rather than hanging', async () => {
		// the handler's promise rejects, which surfaces as a failed subrequest carrying the Java
		// message. A hang is what this looked like before the rejection path worked.
		await expect(env.CORE.fetch('https://bytebox.test/throws')).rejects.toThrow(
			'the handler threw'
		);
	});
});

describe('reading the environment', () => {
	it('reads a variable, and falls back for one that is absent', async () => {
		const result = await route<Record<string, string>>('/vars');

		expect(result.greeting).toBe('hello from the environment');
		expect(result.missing).toBe('null');
		expect(result.fallback).toBe('fell back');
		expect(result.hasKv).toBe('true');
		expect(result.hasNope).toBe('false');
	});

	it('names an undeclared binding instead of failing further along', async () => {
		const result = await route<{ message: string }>('/missing');

		expect(result.message).toContain('VECTORIZE');
		expect(result.message).toContain('bindings block');
	});
});

describe('KV through the Java surface', () => {
	it('writes, reads, lists and deletes', async () => {
		const result = await route<Record<string, string>>('/kv');

		expect(result.read).toBe('hello from kv');
		expect(result.absent).toBe('null');
		expect(result.deleted).toBe('null');
		expect(result.keys).toBe('greeting');
	});
});

describe('R2 through the Java surface', () => {
	it('writes, reads its metadata, and deletes', async () => {
		const result = await route<Record<string, string>>('/r2');

		expect(result.read).toBe('hello from r2');
		expect(result.key).toBe('greeting.txt');
		expect(result.size).toBe('13');
		expect(result.absent).toBe('null');
		expect(result.gone).toBe('null');
	});
});

describe('D1 through the Java surface', () => {
	it('executes, binds, reads one row and reads many', async () => {
		const result = await route<Record<string, string>>('/d1');

		expect(result.size).toBe('3');
		expect(result.names).toBe('core,plugin');
		expect(result.rows).toBe('2');
		expect(result.changes).toBe('1');
	});
});

describe('suspension from Java', () => {
	it('resumes a fiber that another fiber resolved', async () => {
		expect(await route('/resolve')).toEqual({ value: 'value' });
	});

	it('resumes a fiber that another fiber failed, with the Java exception intact', async () => {
		// the rejection path resolves a JavaScript class by name before it can resume the waiter,
		// so an unwarmed global turned every failed operation into a hang rather than a throw
		expect(await route('/reject')).toEqual({ outcome: 'caught IllegalStateException' });
	});

	it('runs work on fibers, joins them, and recovers from one that threw', async () => {
		const result = await route<Record<string, string>>('/async');

		expect(result.first).toBe('first done');
		expect(result.second).toBe('second done');
		expect(result.recovered).toBe('fell back');
		// the clock advances across a sleep, which is what makes a sleep terminate at all
		expect(result.clockMoved).toBe('true');
	});
});

describe('the platform builtins', () => {
	it('hashes, encodes and formats through the runtime rather than in the binary', async () => {
		const result = await route<Record<string, string>>('/builtin');

		// SHA-256 of "hello world!"
		expect(result.sha256).toBe('7509e5bda0c762d2');
		expect(result.base64).toBe('SGVsbG8=');
		expect(result.hexRoundTrip).toBe('cafe00');
		expect(result.uuidLength).toBe('36');
		expect(result.iso).toBe('1970-01-01T00:00:00.000Z');
	});

	it('carries real locale data rather than falling back to one locale', async () => {
		const result = await route<Record<string, string>>('/builtin');

		expect(result.number).toBe('1,234.5');
		expect(result.currency).toBe('$9.99');
		expect(result.localeSupported).toBe('true');
	});

	it('carries real timezone data, and reports the runtime as UTC', async () => {
		const result = await route<Record<string, string>>('/builtin');

		expect(result.timeZone).toBe('UTC');
		expect(result.zoneSupported).toBe('true');
		expect(result.dateTime).toContain('1970');
	});
});

describe('TSObject', () => {
	it('reads and writes properties, and walks a parsed document', async () => {
		const result = await route<Record<string, string>>('/tsobject');

		expect(result.name).toBe('bytebox');
		expect(result.port).toBe('8787');
		expect(result.ready).toBe('true');
		expect(result.keys).toBe('name,port,ready');
		expect(result.typeOf).toBe('string');
		expect(result.arrayLength).toBe('3');
		expect(result.nested).toBe('d');
		expect(result.isArray).toBe('true');
		expect(result.missingIsNull).toBe('true');
		expect(JSON.parse(result.json!)).toEqual({ name: 'bytebox', port: 8787, ready: true });
	});
});

describe('numeric conversions', () => {
	it('carries a long as a BigInt, exactly past what a Number holds', async () => {
		const result = await route<Record<string, string>>('/numbers');

		expect(result.longType).toBe('bigint');
		expect(result.longExact).toBe('9007199254740993');
		expect(result.longRoundTrip).toBe('-9223372036854775808');
		expect(result.isBigInt).toBe('true');
	});

	it('carries every other numeric as a Number', async () => {
		const result = await route<Record<string, string>>('/numbers');

		expect(result.intType).toBe('number');
		expect(result.isNumber).toBe('true');
		expect(result.floatKeeps).toBe('1.5');
	});

	it('reads either kind through either reader', async () => {
		const result = await route<Record<string, string>>('/numbers');

		// a Number read as a long, and a BigInt read as an int
		expect(result.numberAsLong).toBe('42');
		expect(result.longAsInt).toBe('2147483647');
	});

	it('narrows the way a Java cast narrows', async () => {
		const result = await route<Record<string, string>>('/numbers');

		// (short) 70000 wraps to 4464 and (byte) 70000 to 112, as in Java
		expect(result.shortWraps).toBe('4464');
		expect(result.byteWraps).toBe('112');
	});

	it('saturates rather than wrapping when a value will not fit a long', async () => {
		const result = await route<Record<string, string>>('/numbers');

		expect(result.saturatesUp).toBe('9223372036854775807');
		expect(result.saturatesDown).toBe('-9223372036854775808');
		expect(result.nanIsZero).toBe('0');
	});

	it('treats a char as a string rather than as its code unit', async () => {
		const result = await route<Record<string, string>>('/numbers');

		expect(result.charIsString).toBe('string');
		expect(result.charOfNumber).toBe('A');
		// reading a char off a string takes its first character
		expect(result.charOfString).toBe('7');
	});
});

describe('collection conversions', () => {
	it('builds and reads an array', async () => {
		const result = await route<Record<string, string>>('/collections');

		expect(result.array).toBe('a|b|c');
		expect(result.arrayLength).toBe('3');
		expect(result.streamed).toBe('abc');
		expect(result.asSet).toBe('3');
	});

	it('builds a real Set, which drops duplicates', async () => {
		const result = await route<Record<string, string>>('/collections');

		expect(result.setIsSet).toBe('true');
		expect(result.setSize).toBe('3');
		expect(result.setContents).toBe('[1, 2, 3]');
	});

	it('builds a real Map, separately from a plain object', async () => {
		const result = await route<Record<string, string>>('/collections');

		expect(result.mapIsMap).toBe('true');
		expect(result.mapKeys).toBe('only');
		expect(result.mapValue).toBe('value');
		expect(result.objectIsMap).toBe('false');
	});

	it('converts a nested collection all the way down', async () => {
		const result = await route<Record<string, string>>('/collections');

		expect(result.objectNested).toBe('[1, 2]');
	});

	it('appends to an array it built', async () => {
		expect((await route<Record<string, string>>('/collections')).pushed).toBe('pushed');
	});
});

describe('JSON codecs', () => {
	it('round-trips a record through a codec, long included', async () => {
		const result = await route<Record<string, string>>('/json');

		expect(result.sku).toBe('A-1');
		expect(result.quantity).toBe('3');
		// a long survives exactly, which is what the BigInt mapping is for
		expect(result.total).toBe('9007199254740993');
		expect(result.weight).toBe('1.5');
		expect(result.paid).toBe('true');
		expect(result.status).toBe('SHIPPED');
		expect(result.tags).toBe('a|b');
		expect(result.roundTrips).toBe('true');
		expect(result.handles).toBe('true');
	});

	it('encodes back to the shape it decoded from', async () => {
		const result = await route<Record<string, string>>('/json');
		const encoded = JSON.parse(result.encoded!) as Record<string, unknown>;

		expect(encoded.sku).toBe('A-1');
		expect(encoded.quantity).toBe(3);
		expect(encoded.weight).toBe(1.5);
		expect(encoded.paid).toBe(true);
		expect(encoded.status).toBe('SHIPPED');
		expect(encoded.tags).toEqual(['a', 'b']);
	});

	it('writes a long as a string, which is what makes it exact', async () => {
		const result = await route<Record<string, string>>('/json');
		const encoded = JSON.parse(result.encoded!) as Record<string, unknown>;

		// JSON.stringify refuses a BigInt, and a number would round above 2^53
		expect(encoded.total).toBe('9007199254740993');
	});

	it('cannot recover a long written as a JSON number above 2^53', async () => {
		const result = await route<Record<string, string>>('/json');

		// JSON.parse rounds it before any bytebox code runs, so nothing here can fix it. The string
		// form above is the one that survives.
		expect(result.numberFormRounds).toBe('9007199254740992');
	});
});
