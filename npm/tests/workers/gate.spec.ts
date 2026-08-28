import { describe, expect, it } from 'vitest';
import type { GateStats } from '../../src/gate.js';
import { drive } from './drive.js';

describe('the gate on workerd', () => {
	it('holds a second entrant until the first has finished suspending', async () => {
		const result = await drive<{ order: string[]; stats: GateStats }>('/gate');

		// without the gate, the second call would land between the first's two halves
		expect(result.order).toEqual(['first in', 'first out', 'second in']);
		expect(result.stats.conflicts).toBe(1);
		expect(result.stats.entered).toBe(2);
		expect(result.stats.peakWaiting).toBe(1);
		expect(result.stats.outstanding).toBe(0);
	});

	it('refuses a synchronous entry while it is held', async () => {
		expect(await drive('/gate/reentrant')).toEqual({
			refused: true,
			code: 'bytebox.reentrancy'
		});
	});
});
