import { env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

declare global {
	namespace Cloudflare {
		interface Env {
			CORE: Fetcher;
		}
	}
}

interface Rewritten {
	body?: string;
	log: string;
	failed?: string;
	message?: string;
}

const DOC =
	'<!DOCTYPE html><html><head><title>t</title></head><body><!-- note --><a href="/x">link</a></body></html>';

async function rewrite(name: string): Promise<Rewritten> {
	const response = await env.CORE.fetch(`https://bytebox.test/htmlrewrite?case=${name}`);
	if (!response.ok)
		throw new Error(`${name} answered ${response.status}: ${await response.text()}`);
	return (await response.json()) as Rewritten;
}

describe('HTMLRewriter from Java', () => {
	describe('element attributes', () => {
		it('reads, sets and removes attributes and reads the tag and namespace', async () => {
			const result = await rewrite('attributes');

			expect(result.log).toBe(
				'a|/x|null|true|false|{href=/x, class=c}|http://www.w3.org/1999/xhtml'
			);
			expect(result.body).toBe('<a href="/y" target="_blank">l</a>');
		});

		it('renames the tag', async () => {
			expect((await rewrite('tag')).body).toBe('<strong>x</strong>');
		});
	});

	describe('element content', () => {
		it('removes, keeps the content of, and replaces elements, and reports removal', async () => {
			const result = await rewrite('removal');

			expect(result.body).toBe('<p>a</p>keptnew<p>b</p>');
			expect(result.log).toBe('removed=true');
		});

		it('inserts before, after, at the start and at the end', async () => {
			expect((await rewrite('insert')).body).toBe('<div>B<p>PxA</p>F</div>');
		});

		it('escapes content as text unless the html option is set', async () => {
			expect((await rewrite('escape')).body).toBe('<p>x&lt;b&gt;&amp;&lt;/b&gt;</p>');
			expect((await rewrite('escapehtml')).body).toBe('<p>x<b>&</b></p>');
		});

		it('replaces the inner content as text and as html', async () => {
			expect((await rewrite('inner')).body).toBe('<p>&lt;i&gt;n&lt;/i&gt;</p>');
			expect((await rewrite('innerhtml')).body).toBe('<p><i>n</i></p>');
		});

		it('inserts html around elements, inside them, and in place of them', async () => {
			expect((await rewrite('htmlall')).body).toBe(
				'<div><u>1</u><p><u>3</u>x<u>4</u></p><u>2</u><s>r</s></div>'
			);
		});

		it('edits end tags: after, remove and rename', async () => {
			expect((await rewrite('endtagops')).body).toBe('<p>a</p>A<i>b<u>c</w>');
		});

		it('runs an end tag handler, and one that waits', async () => {
			const plain = await rewrite('endtag');
			expect(plain.body).toBe('<p>hi!</p><p>yo!</p>');
			expect(plain.log).toBe('p;p;');

			expect((await rewrite('endtagwait')).body).toBe('<p>hi?</p><p>yo?</p>');
		});
	});

	describe('text and comments', () => {
		it('hands over text in chunks, the last one marked, and appends after it', async () => {
			const result = await rewrite('text');

			expect(result.log).toBe('[Hello world:false][:true]');
			expect(result.body).toBe('<p>Hello world!</p>');
		});

		it('replaces a chunk', async () => {
			expect((await rewrite('textbefore')).body).toBe('<p>HELLO WORLD</p>');
		});

		it('inserts before and after, and removes, a chunk', async () => {
			const result = await rewrite('textops');

			expect(result.body).toBe('<p>[ab]</p><q></q>');
			expect(result.log).toBe('true;true;');
		});

		it('inserts and replaces text and comments as html', async () => {
			expect((await rewrite('htmlmore')).body).toBe(
				'<p><b>t</b><i><!--c--></i><s></p><u><q><em>Z</em></q>'
			);
		});

		it('inserts before and after, and replaces, a comment', async () => {
			const result = await rewrite('commentops');

			expect(result.body).toBe('<div>[<!--a-->B<!--c-->]</div>');
			expect(result.log).toBe('removed=true');
		});

		it('reads, edits and removes comments', async () => {
			const result = await rewrite('comments');

			expect(result.log).toBe('[ note ][ drop ]');
			expect(result.body).toBe('<div><!-- edited --></div>');
		});

		it('gives a class that implements three handlers its element, text and comment', async () => {
			expect((await rewrite('both')).log).toBe('element;text:t;comment: c ;');
		});
	});

	describe('document handlers', () => {
		it('reads a doctype with a public and a system id', async () => {
			expect((await rewrite('doctype')).log).toBe(
				'html|-//W3C//DTD HTML 4.01//EN|http://www.w3.org/TR/html4/strict.dtd'
			);
		});

		it('reads the doctype, text and comments and appends a footer at the end', async () => {
			const result = await rewrite('document');

			expect(result.log).toBe('html|null|null;text:x;comment: c ;');
			expect(result.body).toBe(
				'<!DOCTYPE html><html><body>x<!-- c --></body></html><footer>f</footer>'
			);
		});
	});

	describe('selectors', () => {
		const SELECTED: [string, string[]][] = [
			['*', ['d', 'p1', 'p2', 'b1', 'p3', 's1', 'sec', 'p4']],
			['p', ['p1', 'p2', 'p3', 'p4']],
			['p:nth-child(2)', ['p2']],
			['p:first-child', ['p1', 'p4']],
			['p:nth-of-type(2)', ['p2']],
			['p:first-of-type', ['p1', 'p4']],
			['p:not([t])', ['p1', 'p4']],
			['.w', ['d', 's1']],
			['#a', ['d']],
			['[t]', ['d', 'p2', 'p3']],
			['[t="BAR"]', ['p2']],
			['[t="bar" i]', ['p2']],
			['[t="Bar baz" s]', ['d']],
			['[t~="baz"]', ['d']],
			['[t^="Bar"]', ['d']],
			['[t$="x"]', ['p3']],
			['[t*="ar"]', ['d', 'p3']],
			['[lang|="en"]', ['d']],
			['div p', ['p1', 'p2', 'p3']],
			['div > b', ['b1']]
		];

		it.each(SELECTED)('matches %s', async (selector, hits) => {
			const response = await env.CORE.fetch(
				`https://bytebox.test/htmlrewrite?case=select&sel=${encodeURIComponent(selector)}`
			);
			const result = (await response.json()) as Rewritten;

			expect(result.log).toBe(hits.map((n) => `${n},`).join(''));
		});

		it('calls two handlers for one element in registration order', async () => {
			const result = await rewrite('two');

			expect(result.log).toBe('a;x;');
			expect(result.body).toBe('<a class="x" data-a="1" data-x="1">l</a>');
		});

		it('passes the platform selector forms through', async () => {
			expect((await rewrite('selectors')).body).toBe(
				'<div id="a" class="w" data-class="1" data-id="1" data-attr="1">' +
					'<p data-first="1">1</p><p data-second="1">2</p></div><span data-not="1">s</span>'
			);
		});
	});

	describe('handlers that wait and handlers that throw', () => {
		it('lets a handler wait on a timer before it edits the element', async () => {
			expect((await rewrite('await')).body).toBe(
				DOC.replace('href="/x"', 'href="/x" data-waited="yes"')
			);
		});

		it('lets a handler call a binding', async () => {
			expect((await rewrite('binding')).body).toBe(
				DOC.replace('href="/x"', 'href="/x" data-kv="/x"')
			);
		});

		it('errors the body with the exception when a handler throws', async () => {
			const result = await rewrite('throw');

			expect(result.failed).toBe('dev.gmitch215.bytebox.concurrent.JSRejection');
			expect(result.message).toContain('boom');
			expect(result.body).toBeUndefined();
		});
	});

	describe('a rewritten response the handler returns unread', () => {
		async function returned(name: string, id: string): Promise<Response> {
			return env.CORE.fetch(`https://bytebox.test/htmlreturn?case=${name}&id=${id}`);
		}

		it('rewrites while the platform streams it to the caller', async () => {
			const response = await returned('plain', '5');

			expect(await response.text()).toBe(DOC.replace('href="/x"', 'href="/x" data-id="5"'));
		});

		it('truncates or errors the body for the caller when a handler throws', async () => {
			const response = await returned('throw', '1');

			expect(response.status).toBe(200);
			await expect(response.text()).rejects.toThrow(/boom/);
		});

		it('keeps eight requests with waiting handlers apart', async () => {
			const ids = ['1', '2', '3', '4', '5', '6', '7', '8'];
			const bodies = await Promise.all(
				ids.map(async (id) => (await returned('slow', id)).text())
			);

			expect(bodies).toEqual(
				ids.map((id) =>
					DOC.replace('href="/x">link', `href="/x" data-id="${id}">link${id}`)
				)
			);
		});

		it('had handlers from several requests parked at once in that run', async () => {
			const peak = Number(await (await returned('peak', '0')).text());

			expect(peak).toBeGreaterThan(1);
		});
	});
});
