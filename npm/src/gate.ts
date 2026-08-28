import { ReentrancyError } from './errors.js';

export interface GateStats {
	/** Calls that have started running. */
	entered: number;
	/** Calls queued or running right now. */
	outstanding: number;
	/** The most that ever queued behind a running call. */
	peakWaiting: number;
	/** Calls that arrived while another was outstanding. Zero means nothing ever overlapped. */
	conflicts: number;
}

export interface Gate {
	/** Queues behind whatever is outstanding. */
	run<T>(fn: () => T | Promise<T>): Promise<T>;
	/** Runs now, or throws {@link ReentrancyError} if a call is in flight. */
	enter<T>(fn: () => T, label?: string): T;
	/** True while a call is running, including one parked in a suspension. */
	held(): boolean;
	stats(): GateStats;
}

/**
 * Serialises entry into one compiled module.
 *
 * A Java call that suspends has yielded to the JavaScript event loop, and the runtime will deliver
 * the next request into the same isolate while the first is parked. Two requests sharing one wasm
 * heap corrupt each other, so every entry goes through here.
 */
export function createGate(): Gate {
	let tail: Promise<unknown> = Promise.resolve();
	// counted when the call is queued rather than when it starts, so two entries arriving in one
	// synchronous turn register as the overlap they are
	let outstanding = 0;
	let depth = 0;
	let entered = 0;
	let peakWaiting = 0;
	let conflicts = 0;

	return {
		run(fn) {
			if (outstanding > 0) conflicts++;
			outstanding++;
			peakWaiting = Math.max(peakWaiting, outstanding - 1);

			const started = tail.then(() => {
				depth++;
				entered++;
				return fn();
			});
			const settled = started.finally(() => {
				depth--;
				outstanding--;
			});
			// the chain must not stay rejected, or one thrown call would block every later one
			tail = settled.catch(() => {});
			return settled;
		},
		enter(fn, label = 'the module') {
			if (depth > 0) {
				conflicts++;
				throw new ReentrancyError(label);
			}
			outstanding++;
			depth++;
			entered++;
			try {
				return fn();
			} finally {
				depth--;
				outstanding--;
			}
		},
		held: () => depth > 0,
		stats: () => ({ entered, outstanding, peakWaiting, conflicts })
	};
}
