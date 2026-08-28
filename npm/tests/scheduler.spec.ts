import { describe, expect, it } from 'vitest';
import { BudgetError } from '../src/errors.js';
import { createScheduler, FIBER_BUDGET, type Scheduler } from '../src/scheduler.js';

/** Offers a fiber the way the compiled module's `teavmAsync.offer` import does. */
function offer(scheduler: Scheduler, due: number, fn: () => void): number {
	return scheduler.imports.offer(null, fn, due);
}

describe('the fiber queue', () => {
	it('runs a fiber with the argument it was offered against', () => {
		const scheduler = createScheduler({ now: () => 100 });
		const seen: unknown[] = [];
		scheduler.imports.offer('the instance', (arg) => seen.push(arg), 0);

		expect(scheduler.pending()).toBe(1);
		expect(scheduler.drain()).toMatchObject({ ran: 1, pending: 0, drained: true });
		expect(seen).toEqual(['the instance']);
	});

	it('runs fibers due together in the order they were offered', () => {
		const scheduler = createScheduler({ now: () => 0 });
		const order: string[] = [];
		offer(scheduler, 0, () => order.push('first'));
		offer(scheduler, 0, () => order.push('second'));
		offer(scheduler, 0, () => order.push('third'));

		scheduler.drain();

		expect(order).toEqual(['first', 'second', 'third']);
	});

	it('runs the earliest due fiber first, whatever order they arrived in', () => {
		const scheduler = createScheduler({ now: () => 0 });
		const order: number[] = [];
		offer(scheduler, 30, () => order.push(30));
		offer(scheduler, 10, () => order.push(10));
		offer(scheduler, 20, () => order.push(20));

		scheduler.drain();

		expect(order).toEqual([10, 20, 30]);
	});

	it('picks up a fiber offered by a fiber that is already running', () => {
		const scheduler = createScheduler({ now: () => 0 });
		const order: string[] = [];
		offer(scheduler, 0, () => {
			order.push('outer');
			offer(scheduler, 0, () => order.push('inner'));
		});

		const result = scheduler.drain();

		expect(order).toEqual(['outer', 'inner']);
		expect(result).toMatchObject({ ran: 2, drained: true });
	});

	it('reports an empty queue as drained, with nothing due', () => {
		const scheduler = createScheduler();

		expect(scheduler.nextDue()).toBe(-1);
		expect(scheduler.drain()).toEqual({ ran: 0, pending: 0, nextDue: -1, drained: true });
	});

	it('drops a killed fiber', () => {
		const scheduler = createScheduler({ now: () => 0 });
		let ran = false;
		const handle = offer(scheduler, 0, () => (ran = true));

		scheduler.imports.kill(handle);

		expect(scheduler.pending()).toBe(0);
		expect(scheduler.drain().ran).toBe(0);
		expect(ran).toBe(false);
	});

	it('ignores a kill for a fiber that already ran', () => {
		const scheduler = createScheduler({ now: () => 0 });
		const handle = offer(scheduler, 0, () => {});
		scheduler.drain();

		expect(() => scheduler.imports.kill(handle)).not.toThrow();
		expect(scheduler.pending()).toBe(0);
	});

	it('drops everything on clear', () => {
		const scheduler = createScheduler({ now: () => 0 });
		offer(scheduler, 0, () => {});
		offer(scheduler, 0, () => {});

		scheduler.clear();

		expect(scheduler.pending()).toBe(0);
	});
});

describe('the budget', () => {
	it('stops the drain and reports what is left', () => {
		const scheduler = createScheduler({ now: () => 0, autoDrain: false });
		for (let i = 0; i < 5; i++) offer(scheduler, 0, () => {});

		const result = scheduler.drain({ budget: 2 });

		expect(result).toMatchObject({ ran: 2, pending: 3, drained: false });
	});

	it('reports how long until the next fiber when it stops short', () => {
		const scheduler = createScheduler({ now: () => 100, autoDrain: false });
		offer(scheduler, 100, () => {});
		offer(scheduler, 250, () => {});

		expect(scheduler.drain({ budget: 1 })).toMatchObject({ ran: 1, nextDue: 150 });
	});

	it('throws instead of reporting when asked to', () => {
		const scheduler = createScheduler({ now: () => 0, autoDrain: false });
		offer(scheduler, 0, () => {});
		offer(scheduler, 0, () => {});

		expect(() => scheduler.drain({ budget: 1, throwOnBudget: true })).toThrow(BudgetError);
	});

	it('does not throw when the budget was exactly enough', () => {
		const scheduler = createScheduler({ now: () => 0 });
		offer(scheduler, 0, () => {});

		expect(scheduler.drain({ budget: 1, throwOnBudget: true }).drained).toBe(true);
	});

	it('carries the counts on the error', () => {
		const scheduler = createScheduler({ now: () => 0, autoDrain: false });
		for (let i = 0; i < 4; i++) offer(scheduler, 0, () => {});

		try {
			scheduler.drain({ budget: 1, throwOnBudget: true });
			expect.unreachable('the budget was one fiber short of four');
		} catch (error) {
			expect(error).toBeInstanceOf(BudgetError);
			expect(error).toMatchObject({ ran: 1, pending: 3, code: 'bytebox.budget_exhausted' });
		}
	});

	it('has a budget for every trigger, and the smallest is fetch', () => {
		const budgets = Object.values(FIBER_BUDGET);

		expect(Object.keys(FIBER_BUDGET)).toEqual([
			'fetch',
			'scheduled',
			'email',
			'queue',
			'tail',
			'alarm'
		]);
		expect(FIBER_BUDGET.fetch).toBe(Math.min(...budgets));
	});
});

describe('virtual time', () => {
	it('runs a fiber that is not due yet, and moves the clock to meet it', () => {
		const scheduler = createScheduler({ policy: 'virtual', now: () => 1000 });
		let ran = false;
		offer(scheduler, 1500, () => (ran = true));

		expect(scheduler.nextDue()).toBe(500);
		expect(scheduler.drain().drained).toBe(true);
		expect(ran).toBe(true);
		expect(scheduler.now()).toBe(1500);
	});

	it('is the default, because Workers pin the clock between I/O', () => {
		const scheduler = createScheduler({ now: () => 0 });
		offer(scheduler, 5000, () => {});

		expect(scheduler.drain().drained).toBe(true);
	});

	it('keeps the clock monotonic across a chain of sleeps', () => {
		const scheduler = createScheduler({ now: () => 0 });
		const seen: number[] = [];
		offer(scheduler, 100, () => {
			seen.push(scheduler.now());
			offer(scheduler, scheduler.now() + 50, () => seen.push(scheduler.now()));
		});

		scheduler.drain();

		expect(seen).toEqual([100, 150]);
	});

	it('does not move the clock backwards for a fiber already overdue', () => {
		const scheduler = createScheduler({ now: () => 1000 });
		offer(scheduler, 200, () => {});

		scheduler.drain();

		expect(scheduler.now()).toBe(1000);
	});
});

describe('real time', () => {
	it('stops the drain at a fiber that is not due yet', () => {
		const scheduler = createScheduler({ policy: 'real', now: () => 0 });
		let ran = false;
		offer(scheduler, 10_000, () => (ran = true));

		const result = scheduler.drain();

		expect(result).toMatchObject({ ran: 0, pending: 1, drained: false, nextDue: 10_000 });
		expect(ran).toBe(false);
		expect(scheduler.now()).toBe(0);
	});

	it('waits for it in drainAsync', async () => {
		const scheduler = createScheduler({ policy: 'real' });
		let ran = false;
		offer(scheduler, Date.now() + 5, () => (ran = true));

		expect(await scheduler.drainAsync()).toMatchObject({ ran: 1, drained: true });
		expect(ran).toBe(true);
	});

	it('gives up rather than spinning when the clock does not advance', async () => {
		// a clock that never moves is what a Worker request without I/O looks like
		const scheduler = createScheduler({ policy: 'real', now: () => 0 });
		offer(scheduler, 5, () => {});

		const result = await scheduler.drainAsync();

		expect(result).toMatchObject({ ran: 0, pending: 1, drained: false });
	});

	it('throws on an exhausted budget in drainAsync too', async () => {
		const scheduler = createScheduler({ policy: 'real', now: () => 0, autoDrain: false });
		offer(scheduler, 0, () => {});
		offer(scheduler, 0, () => {});

		await expect(scheduler.drainAsync({ budget: 1, throwOnBudget: true })).rejects.toThrow(
			BudgetError
		);
	});

	it('runs what is already due before waiting on what is not', async () => {
		const scheduler = createScheduler({ policy: 'real', now: () => 0 });
		const order: string[] = [];
		offer(scheduler, 0, () => order.push('due'));
		offer(scheduler, 5, () => order.push('later'));

		await scheduler.drainAsync();

		expect(order[0]).toBe('due');
	});
});

describe('driving itself', () => {
	const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

	it('runs a fiber offered while nothing was draining', async () => {
		const scheduler = createScheduler({ now: () => 0 });
		let ran = false;
		offer(scheduler, 0, () => (ran = true));

		expect(ran).toBe(false);
		await settle();

		expect(ran).toBe(true);
		expect(scheduler.pending()).toBe(0);
	});

	it('picks up what a budgeted drain left behind', async () => {
		const scheduler = createScheduler({ now: () => 0 });
		let ran = 0;
		for (let i = 0; i < 5; i++) offer(scheduler, 0, () => ran++);

		expect(scheduler.drain({ budget: 2 }).pending).toBe(3);
		await settle();

		expect(ran).toBe(5);
	});

	it('leaves a fiber that is not due alone rather than spinning on it', async () => {
		const scheduler = createScheduler({ policy: 'real', now: () => 0 });
		let ran = false;
		offer(scheduler, 60_000, () => (ran = true));

		await settle();

		expect(ran).toBe(false);
		expect(scheduler.pending()).toBe(1);
	});

	it('does nothing when turned off', async () => {
		const scheduler = createScheduler({ now: () => 0, autoDrain: false });
		let ran = false;
		offer(scheduler, 0, () => (ran = true));

		await settle();

		expect(ran).toBe(false);
		expect(scheduler.pending()).toBe(1);
	});

	it('runs a fiber a running fiber offered, without re-entering the module', async () => {
		const scheduler = createScheduler({ now: () => 0 });
		const order: string[] = [];
		offer(scheduler, 0, () => {
			order.push('outer');
			offer(scheduler, 0, () => order.push('inner'));
		});

		await settle();

		expect(order).toEqual(['outer', 'inner']);
	});
});
