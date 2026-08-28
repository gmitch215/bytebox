import { BudgetError } from './errors.js';

/**
 * How a fiber scheduled for the future is treated.
 *
 * `virtual` moves the scheduler's clock forward to the fiber's due time and runs it, so
 * `Thread.sleep` costs no wall time. `real` leaves the clock alone, so {@link Scheduler.drain}
 * stops at the first fiber that is not due yet and {@link Scheduler.drainAsync} waits for it.
 *
 * Workers pin `Date.now()` between I/O, so under `real` a program that sleeps without doing any
 * I/O never becomes due inside one request. That is why `virtual` is the default.
 */
export type TimePolicy = 'virtual' | 'real';

/** What a drain did. */
export interface DrainResult {
	/** Fibers that ran. */
	ran: number;
	/** Fibers still queued when the drain stopped. */
	pending: number;
	/** Milliseconds until the earliest queued fiber is due, or -1 when none are queued. */
	nextDue: number;
	/** True when the queue emptied, false when a budget, a stall or a future due time stopped it. */
	drained: boolean;
}

export interface DrainOptions {
	/**
	 * Most fibers to run. Defaults to {@link FIBER_BUDGET.fetch}.
	 *
	 * Counted in fibers rather than milliseconds because this runtime exposes no readable CPU
	 * meter: `Date.now()` and `performance.now()` are both pinned between I/O, so nothing inside
	 * a request can measure elapsed time.
	 */
	budget?: number;
	/** Throws {@link BudgetError} instead of returning `drained: false` when the budget runs out. */
	throwOnBudget?: boolean;
}

export interface SchedulerOptions {
	policy?: TimePolicy;
	/** Reads wall time. Defaults to `Date.now`. */
	now?: () => number;
	/**
	 * Whether a fiber offered while nothing is draining schedules its own drain. Defaults to true.
	 *
	 * <p>Without it, every fiber a handler starts after its first suspension needs the host to
	 * notice and drain again, and one it does not notice never runs. With it the queue drives
	 * itself and {@link Scheduler.drain} is an optimisation rather than a requirement.
	 */
	autoDrain?: boolean;
	/** Fiber budget for a drain the scheduler scheduled itself. Defaults to the `fetch` budget. */
	autoBudget?: number;
}

export interface Scheduler {
	/** Install as the module's `teavmAsync` import. */
	readonly imports: {
		offer(arg: unknown, callback: (arg: unknown) => void, due: number): number;
		kill(handle: number): void;
	};
	/**
	 * The clock the scheduler and the compiled program share.
	 *
	 * {@link load} installs this as `teavmDate.currentTimeMillis`, so `System.currentTimeMillis()`
	 * agrees with the scheduler about what time it is. Under `virtual` that makes a slept-through
	 * interval visible to Java rather than leaving its clock frozen behind the queue.
	 */
	now(): number;
	pending(): number;
	/** Milliseconds until the earliest queued fiber is due, or -1 when none are queued. */
	nextDue(): number;
	drain(options?: DrainOptions): DrainResult;
	/** Waits real time for a fiber that is not due yet. Only differs from {@link drain} under `real`. */
	drainAsync(options?: DrainOptions): Promise<DrainResult>;
	/** Drops every queued fiber. */
	clear(): void;
}

/**
 * Per-trigger fiber budgets: a runaway backstop, not a CPU budget.
 *
 * `fetch` gets the smallest because it has the smallest CPU allowance, 10 ms on the free plan.
 * The rest run under allowances measured in minutes.
 */
export const FIBER_BUDGET = {
	fetch: 100_000,
	scheduled: 5_000_000,
	email: 5_000_000,
	queue: 5_000_000,
	tail: 100_000,
	alarm: 100_000
} as const;

export type Trigger = keyof typeof FIBER_BUDGET;

interface Fiber {
	handle: number;
	seq: number;
	due: number;
	arg: unknown;
	callback: (arg: unknown) => void;
}

/**
 * Owns the fiber queue TeaVM would otherwise hand to `setTimeout`.
 *
 * On the WebAssembly GC backend `EventQueue`'s own heap is bypassed: `Thread.start`,
 * `Thread.sleep` and every `@Async` suspension go straight out through the `teavmAsync.offer`
 * import, and the exported `teavm_processQueue` stays empty for the module's whole life. So
 * draining the queue means running the callbacks the host was handed, and owning them is what
 * makes that possible inside a request: a `setTimeout` still outstanding when a handler returns
 * is cancelled unless something holds the request open.
 */
export function createScheduler(options: SchedulerOptions = {}): Scheduler {
	const policy = options.policy ?? 'virtual';
	const wall = options.now ?? Date.now;

	const autoDrain = options.autoDrain ?? true;
	const autoBudget = options.autoBudget ?? FIBER_BUDGET.fetch;

	// under `virtual` this moves forward to the due time of a fiber nothing else precedes
	let offset = 0;
	let queue: Fiber[] = [];
	let handles = 0;
	let sequence = 0;
	let draining = false;
	let scheduled = false;

	const now = () => wall() + offset;

	// linear rather than a heap: one live Java thread contributes at most one queued fiber, so the
	// queue stays short enough that the scan is cheaper than maintaining the invariant
	function earliest(): Fiber | undefined {
		let best: Fiber | undefined;
		for (const fiber of queue) {
			if (best === undefined) best = fiber;
			// seq breaks the tie, so fibers due together run in the order they were offered
			else if (fiber.due < best.due || (fiber.due === best.due && fiber.seq < best.seq)) {
				best = fiber;
			}
		}
		return best;
	}

	function take(fiber: Fiber): void {
		queue = queue.filter((queued) => queued !== fiber);
	}

	function result(ran: number): DrainResult {
		const next = earliest();
		return {
			ran,
			pending: queue.length,
			nextDue: next === undefined ? -1 : Math.max(0, next.due - now()),
			drained: queue.length === 0
		};
	}

	/** The next thing to do: run a fiber, wait for one, or stop. */
	function step(): { run: Fiber } | { wait: Fiber } | 'done' {
		const next = earliest();
		if (next === undefined) return 'done';
		if (next.due <= now()) return { run: next };
		if (policy === 'real') return { wait: next };
		offset += next.due - now();
		return { run: next };
	}

	function run(fiber: Fiber): void {
		take(fiber);
		fiber.callback(fiber.arg);
	}

	/**
	 * Runs a drain on a microtask, once, for a fiber that arrived with nothing draining.
	 *
	 * Queued as a microtask rather than run inline because `offer` is called from inside the module,
	 * and re-entering it there would resume a fiber on top of the one that is running.
	 */
	function scheduleDrain(): void {
		if (!autoDrain || draining || scheduled) return;
		// only when something is due. A fiber waiting on a future time cannot be advanced by
		// draining again, and rescheduling for one spins the microtask queue forever
		const next = earliest();
		if (next === undefined || (policy === 'real' && next.due > now())) return;
		scheduled = true;
		queueMicrotask(() => {
			scheduled = false;
			if (draining) return;
			self.drain({ budget: autoBudget });
		});
	}

	const self: Scheduler = {
		imports: {
			offer(arg, callback, due) {
				const handle = ++handles;
				queue.push({ handle, seq: sequence++, due, arg, callback });
				scheduleDrain();
				return handle;
			},
			kill(handle) {
				queue = queue.filter((fiber) => fiber.handle !== handle);
			}
		},
		now,
		pending: () => queue.length,
		nextDue() {
			const next = earliest();
			return next === undefined ? -1 : Math.max(0, next.due - now());
		},
		drain(drainOptions = {}) {
			const budget = drainOptions.budget ?? FIBER_BUDGET.fetch;
			let ran = 0;
			draining = true;
			try {
				while (ran < budget) {
					const next = step();
					if (next === 'done' || 'wait' in next) break;
					run(next.run);
					ran++;
				}
			} finally {
				draining = false;
			}
			const outcome = result(ran);
			// a fiber left queued still needs running, and the drain that would have picked it up
			// has just ended
			if (!outcome.drained) scheduleDrain();
			if (drainOptions.throwOnBudget && ran >= budget && !outcome.drained) {
				throw new BudgetError(ran, outcome.pending);
			}
			return outcome;
		},
		async drainAsync(drainOptions = {}) {
			const budget = drainOptions.budget ?? FIBER_BUDGET.fetch;
			let ran = 0;
			// a timer can fire a shade early, so one fruitless wait is not evidence the clock is
			// stuck; two consecutive ones are, and Workers pin the clock between I/O
			let stalls = 0;
			draining = true;
			try {
				while (ran < budget) {
					const next = step();
					if (next === 'done') break;
					if ('wait' in next) {
						const delay = Math.max(1, next.wait.due - now());
						await new Promise((resolve) => setTimeout(resolve, delay));
						if (next.wait.due > now() && ++stalls >= 2) break;
						continue;
					}
					stalls = 0;
					run(next.run);
					ran++;
				}
			} finally {
				draining = false;
			}
			const outcome = result(ran);
			if (!outcome.drained) scheduleDrain();
			if (drainOptions.throwOnBudget && ran >= budget && !outcome.drained) {
				throw new BudgetError(ran, outcome.pending);
			}
			return outcome;
		},
		clear() {
			queue = [];
		}
	};

	return self;
}
