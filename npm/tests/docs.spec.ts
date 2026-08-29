import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { renderSite } from '../../docs/render.js';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const repo = 'https://example.test/owner/project';

let page: string;

/** The landing page, rendered from the repository's own README rather than from a sample. */
describe('the landing page', () => {
	beforeAll(async () => {
		page = await renderSite({
			readme: await readFile(join(root, 'README.md'), 'utf8'),
			template: await readFile(join(root, 'docs', 'template.html'), 'utf8'),
			repo,
			root
		});
	}, 60_000);

	it('keeps the template around the README', () => {
		expect(page).toContain('<title>bytebox</title>');
		expect(page).toContain('href="javadoc/"');
		expect(page).toContain('href="typedoc/"');
		expect(page).not.toContain('<!-- readme -->');
	});

	it('drops the title and tagline the template already states', () => {
		// the README's own heading would otherwise sit under the template's
		expect(page).not.toContain('<h1>☕ bytebox</h1>\n<blockquote>');
		expect(page.match(/<h1[^>]*>/g)).toHaveLength(1);
	});

	it('gives every heading the id GitHub would, so the table of contents works', () => {
		const anchors = [...page.matchAll(/href="#([^"]+)"/g)].map((match) => match[1] ?? '');

		expect(anchors.length).toBeGreaterThan(5);
		for (const anchor of anchors) {
			expect(page, `#${anchor} has no heading`).toContain(`id="${anchor}"`);
		}
	});

	it('resolves a relative link against the repository, and leaves the rest alone', () => {
		expect(page).toContain(`href="${repo}/tree/master/samples"`);
		expect(page).toContain(`href="${repo}/blob/master/samples/ADVANCED_USAGE.md"`);
		expect(page).toContain(`href="${repo}/blob/master/LICENSE"`);
		// a badge is already absolute
		expect(page).toContain('href="https://codecov.io/gh/gmitch215/bytebox"');
	});

	it('highlights each fence at build time, so the page runs no script', () => {
		expect(page).toContain('class="shiki');
		// both palettes on every token, which is what lets the CSS pick one
		expect(page).toContain('--shiki-dark');
		expect(page).not.toContain('<script');
	});

	it('wraps a table so a wide one scrolls inside itself', () => {
		const tables = page.match(/<table>/g) ?? [];

		expect(tables.length).toBeGreaterThan(3);
		expect(page.match(/<div class="table"><table>/g)).toHaveLength(tables.length);
	});

	it('refuses a template with nowhere to render into', async () => {
		await expect(
			renderSite({ readme: '# t\n\ntext\n', template: '<html></html>', repo })
		).rejects.toThrow('<!-- readme -->');
	});
});
