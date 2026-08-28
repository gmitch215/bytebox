import { describe, expect, it } from 'vitest';
import { ReentrancyError } from '../src/errors.js';
import { createGate } from '../src/gate.js';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('serialising entry', () => {
	it('runs one call straight through', async () => {
		const gate = createGate();

		await expect(gate.run(() => 'value')).resolves.toBe('value');
		expect(gate.stats()).toMatchObject({ entered: 1, conflicts: 0, outstanding: 0 });
	});

	it('holds a second call until the first has finished suspending', async () => {
		const gate = createGate();
		const order: string[] = [];

		const first = gate.run(async () => {
			order.push('first in');
			await settle();
			order.push('first out');
		});
		const second = gate.run(() => order.push('second'));
		await Promise.all([first, second]);

		expect(order).toEqual(['first in', 'first out', 'second']);
	});

	it('counts an overlap even when both calls are queued in one turn', async () => {
		const gate = createGate();

		await Promise.all([gate.run(() => {}), gate.run(() => {}), gate.run(() => {})]);

		expect(gate.stats()).toMatchObject({ entered: 3, conflicts: 2, peakWaiting: 2 });
	});

	it('reports no overlap when calls never coincide', async () => {
		const gate = createGate();

		await gate.run(() => {});
		await gate.run(() => {});

		expect(gate.stats().conflicts).toBe(0);
	});

	it('keeps running later calls after one throws', async () => {
		const gate = createGate();
		const order: string[] = [];

		const failed = gate.run(() => {
			order.push('threw');
			throw new Error('inside');
		});
		const after = gate.run(() => order.push('still ran'));

		await expect(failed).rejects.toThrow('inside');
		await after;

		expect(order).toEqual(['threw', 'still ran']);
		expect(gate.stats().outstanding).toBe(0);
	});

	it('is held while a call is suspended and free once it returns', async () => {
		const gate = createGate();
		let heldInside = false;

		await gate.run(async () => {
			await settle();
			heldInside = gate.held();
		});

		expect(heldInside).toBe(true);
		expect(gate.held()).toBe(false);
	});
});

describe('the synchronous entry', () => {
	it('runs and returns the value', () => {
		const gate = createGate();

		expect(gate.enter(() => 41 + 1)).toBe(42);
		expect(gate.stats()).toMatchObject({ entered: 1, outstanding: 0 });
	});

	it('refuses a nested entry, naming what was entered', () => {
		const gate = createGate();

		try {
			gate.enter(() => gate.enter(() => 'never'), 'the module');
			expect.unreachable('a nested entry is the reentrancy this guards');
		} catch (error) {
			expect(error).toBeInstanceOf(ReentrancyError);
			expect((error as ReentrancyError).code).toBe('bytebox.reentrancy');
			expect((error as ReentrancyError).message).toContain('the module');
		}
	});

	it('frees the gate after a call that threw', () => {
		const gate = createGate();

		expect(() =>
			gate.enter(() => {
				throw new Error('inside');
			})
		).toThrow('inside');
		expect(gate.held()).toBe(false);
		expect(gate.enter(() => 'again')).toBe('again');
	});

	it('refuses while an asynchronous call is parked', async () => {
		const gate = createGate();
		let refused: unknown;

		await gate.run(async () => {
			await settle();
			try {
				gate.enter(() => 'never');
			} catch (error) {
				refused = error;
			}
		});

		expect(refused).toBeInstanceOf(ReentrancyError);
	});
});
