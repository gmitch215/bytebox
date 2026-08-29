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

		expect(result.message).toContain('NOT_DECLARED');
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

describe('futures', () => {
	it('builds from a value, a failure and a promise, and transforms each', async () => {
		const result = await route<Record<string, string>>('/futures');

		expect(result.ready).toBe('ready');
		expect(result.wrapped).toBe('ready');
		expect(result.mapped).toBe('ready mapped');
		expect(result.chained).toBe('then');
		expect(result.recovered).toBe('recovered IllegalStateException');
		expect(result.fallback).toBe('supplied');
	});

	it('waits on several at once, and on whichever settles first', async () => {
		const result = await route<Record<string, string>>('/futures');

		expect(result.all).toBe('first,second');
		expect(result.race).toBe('won');
		expect(result.ran).toBe('side effect');
	});
});

describe('the triggers other than fetch', () => {
	it('runs every handler the class implements, and records what each was given', async () => {
		for (const trigger of ['scheduled', 'email', 'queue', 'tail', 'alarm']) {
			const answer = await env.CORE.fetch(`https://bytebox.test/__trigger/${trigger}`);
			expect(await answer.text(), trigger).toBe(`${trigger} ran`);
		}

		const fired = await (await env.CORE.fetch('https://bytebox.test/fired')).text();

		expect(fired).toContain('cron:*/5 * * * *@1700000000000');
		expect(fired).toContain('mail:sender@example.com->inbox@example.com');
		// forwarding records the disposition on the message, which is what makes the handler void
		expect(fired).toContain('disposition:FORWARD');
		expect(fired).toContain('queue:fixture-queue:m-1=7#1');
		expect(fired).toContain('tail:1:ok');
		expect(fired).toContain('alarm|');
	});
});

describe('the conveniences layered over each binding', () => {
	it('hashes and encodes through every overload', async () => {
		const result = await route<Record<string, string>>('/overloads');

		expect(result.md5).toBe('ok');
		// SHA-1 of "a"
		expect(result.sha1).toBe('86f7e437');
		expect(result.sha512).toBe('128');
		expect(result.sha512Bytes).toBe('128');
		expect(result.md5Bytes).toBe('32');
		expect(result.sha1Bytes).toBe('40');
		expect(result.digest).toBe('64');
		expect(result.hmacBytes).toBe('64');
		expect(result.sameText).toBe('true');
		expect(result.randomBytes).toBe('32');
		expect(result.decoded).toBe('signed');
		expect(result.base64).toBe('AQID');
		expect(result.cookies).toBe('0');
	});

	it('reads and writes KV, R2 and D1 through their siblings', async () => {
		const result = await route<Record<string, string>>('/overloads');

		expect(result.kvJson).toBe('true');
		expect(result.kvTag).toBe('a');
		expect(result.kvListed).toBe('true');
		expect(result.r2Range).toBe('eight');
		expect(result.r2Listed).toBe('true');
		expect(result.r2Names).toBe('true');
		// SQLite has no boolean, so a bound true is stored and read back as 1
		expect(result.d1Flag).toBe('1');
		expect(result.d1Column).toBe('1');
		expect(result.d1Batch).toBe('2');
	});
});

describe('a Durable Object', () => {
	it('derives a stable id from a name and a fresh one on request', async () => {
		const result = await route<Record<string, string>>('/durable');

		expect(result.name).toBe('global');
		expect(result.stable).toBe('true');
		expect(result.unique).toBe('true');
		expect(result.stubId).toBe('true');
	});

	it('reads and writes the instance storage, keeping types across the boundary', async () => {
		const result = await route<Record<string, string>>('/durable');

		expect(result.count).toBe('42');
		expect(result.nested).toBe('true');
		expect(result.removed).toBe('true');
		// a second delete of the same key reports that there was nothing to remove
		expect(result.again).toBe('false');
	});

	it('queries the instance SQL store', async () => {
		const result = await route<Record<string, string>>('/durable');

		expect(result.rows).toBe('2');
		expect(result.first).toBe('core');
		expect(result.seen).toBe('2');
	});

	it('sets and clears an alarm', async () => {
		const result = await route<Record<string, string>>('/durable');

		expect(result.alarmSet).toBe('true');
		expect(result.alarmCleared).toBe('0');
	});

	it('accepts a websocket and answers on it', async () => {
		const upgraded = await env.CORE.fetch('https://bytebox.test/durablesocket', {
			headers: { upgrade: 'websocket' }
		});
		const socket = upgraded.webSocket;

		expect(upgraded.status).toBe(101);
		expect(socket).toBeTruthy();
		socket!.accept();

		const answers: string[] = [];
		const heard = new Promise<void>((resolve) => {
			socket!.addEventListener('message', (event) => {
				answers.push(String(event.data));
				if (answers.length === 3) resolve();
			});
		});
		socket!.send('count');
		await heard;

		expect(answers[0]).toBe('echo count');
		expect(answers[1]).toBe('sockets 1');
		expect(answers[2]).toBe('broadcast');
	});

	it('carries bytes over the socket and records the close', async () => {
		const upgraded = await env.CORE.fetch('https://bytebox.test/durablesocket', {
			headers: { upgrade: 'websocket' }
		});
		const socket = upgraded.webSocket!;
		socket.accept();

		const echoed = new Promise<Blob | ArrayBuffer>((resolve) => {
			socket.addEventListener('message', (event) => resolve(event.data as Blob));
		});
		socket.send(new Uint8Array([1, 2, 3, 4]));
		// a binary frame reaches a client socket as a Blob, not as the buffer that was sent
		const back = await echoed;
		const bytes = back instanceof ArrayBuffer ? back : await (back as Blob).arrayBuffer();
		socket.close(1000, 'done');

		expect(new Uint8Array(bytes)).toEqual(new Uint8Array([1, 2, 3, 4]));

		// the close is delivered after the response, so the record is read on a later request
		const seen = await (await env.CORE.fetch('https://bytebox.test/durableseen')).text();
		expect(seen).toContain('bytes:4');
		expect(seen).toContain('closed:1000');
	});
});

describe('a promise that rejects', () => {
	// awaiting an Error rejection used to hang the fiber, and every binding goes through the same
	// wait, so a service that refused took the whole invocation with it
	it('throws rather than hanging, whatever JavaScript rejected with', async () => {
		const result = await route<Record<string, string>>('/rejects');

		expect(result.error).toBe('JSRejection');
		expect(result.string).toBe('JSRejection');
		expect(result.nothing).toBe('JSRejection');
	});

	it('rethrows the original exception when the rejection carries one', async () => {
		const result = await route<Record<string, string>>('/reject');

		expect(result.outcome).toBe('caught IllegalStateException');
	});

	it('surfaces a refused subrequest as a throw', async () => {
		const result = await route<Record<string, string>>('/fetch');

		expect(result.refusedOutright).toBe('JSRejection');
	});
});

describe('outbound HTTP', () => {
	it('fetches, and reads the answer as text, JSON and bytes', async () => {
		const result = await route<Record<string, string>>('/fetch');

		expect(result.method).toBe('GET');
		expect(result.query).toBe('?q=1');
		expect(result.echoed).toBe('seen');
		expect(result.posted).toBe('sent with options');
		expect(Number(result.rawLength)).toBeGreaterThan(0);
	});

	it('refuses a failing response by status rather than by parsing it', async () => {
		const result = await route<Record<string, string>>('/fetch');

		expect(result.refused).toContain('503');
	});

	it('builds a response from bytes, from a JavaScript value and from a codec', async () => {
		const result = await route<Record<string, string>>('/fetch');

		expect(result.builtType).toBe('application/octet-stream');
		expect(result.builtStatus).toBe('200');
		expect(result.codec).toContain('"sku":"A-1"');
	});

	it('drives HttpURLConnection, which is what an older library reaches for', async () => {
		const result = await route<Record<string, string>>('/urlconnection');

		expect(result.status).toBe('200');
		expect(result.host).toBe('echo.test');
		expect(result.path).toBe('/echo');
		expect(result.query).toBe('from=urlconnection');
		expect(result.protocol).toBe('http');
		expect(result.seen).toBe('seen');
		expect(result.body).toContain('"method":"POST"');
	});

	it('reads a failing response off the error stream rather than throwing it away', async () => {
		const result = await route<Record<string, string>>('/urlconnection');

		expect(result.failed).toBe('404');
		expect(result.error).toBe('the server said 404');
	});

	it('drives java.net.http.HttpClient, which is what a current one reaches for', async () => {
		const result = await route<Record<string, string>>('/httpclient');

		expect(result.status).toBe('200');
		expect(result.method).toBe('POST');
		expect(result.body).toContain('"fixture":"httpclient"');
		expect(result.header).toBe('seen');
		expect(result.redirects).toBe('NORMAL');
		expect(Number(result.byteLength)).toBeGreaterThan(0);
		expect(result.discarded).toBe('204');
	});
});

describe('the bindings that are a service rather than a store', () => {
	it('calls a service binding, an mTLS one and browser rendering', async () => {
		const result = await route<Record<string, string>>('/services');

		expect(result.service).toBe('/echo');
		expect(result.mtls).toBe('/echo');
		expect(result.browser).toBe('/echo');
		expect(result.method).toBe('PUT');
		expect(result.byRequest).toBe('/echo');
		expect(result.rpc).toBe('true');
	});

	it('reads an image and runs a transformation', async () => {
		const result = await route<Record<string, string>>('/services');

		expect(result.aiOptions).toBe('a fixture answer');
		expect(result.embedded).toBe('a fixture answer');
		expect(result.format).toBe('image/png');
		expect(result.width).toBe('4');
		expect(result.rendered).toBe('true');
	});

	it('starts, signals and stops a container', async () => {
		const result = await route<Record<string, string>>('/services');

		expect(result.running).toBe('false');
		expect(result.port).toBe('8080');
	});
});

describe('Java arrays crossing into JavaScript', () => {
	// the interop copies a byte[] through a staging buffer in linear memory, and that heap is sized
	// by `minDirectBuffersSize`. At zero it is initialised empty and the copy loops forever, so this
	// route stops answering rather than failing.
	it('round-trips bytes through a buffer and through a view', async () => {
		const result = await route<Record<string, string>>('/bytes');

		expect(result.roundTrip).toBe('007f80ff2a');
		expect(result.windowed).toBe('007f80ff2a');
		expect(result.decoded).toBe('héllo');
	});

	it('feeds the byte[] overloads of the builtins', async () => {
		const result = await route<Record<string, string>>('/bytes');

		// SHA-256 of 00 7f 80 ff 2a
		expect(result.digest).toBe('aac52de78671d4ea');
		expect(result.equal).toBe('true');
		expect(result.differ).toBe('false');
	});

	it('writes and reads bytes through KV and R2', async () => {
		const result = await route<Record<string, string>>('/bytes');

		expect(result.fromKv).toBe('007f80ff2a');
		expect(result.fromR2).toBe('007f80ff2a');
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
