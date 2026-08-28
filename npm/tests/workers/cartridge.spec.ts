import { describe, expect, it } from 'vitest';
import { drive } from './drive.js';

describe('a compiled program driven as a cartridge interpreter', () => {
	it('runs main with the argv it was given and reads the mounted file back', async () => {
		const result = await drive<{ status: number; out: string[]; files: string[] }>(
			'/cartridge'
		);

		expect(result.status).toBe(0);
		expect(result.out).toEqual(['argv:java,/cartridge/main.txt', 'read:from the cartridge']);
		expect(result.files).toEqual(['/cartridge/main.txt']);
	});
});
