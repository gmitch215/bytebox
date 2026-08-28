import { describe, expect, it } from 'vitest';
import type { DrainResult } from '../../src/scheduler.js';
import { drive } from './drive.js';

describe('fibers on workerd', () => {
	it('queues a started thread instead of running it inline, and runs it on drain', async () => {
		const result = await drive<{
			beforeDrain: { out: string[]; pending: number };
			afterDrain: { out: string[] };
			result: DrainResult;
		}>('/queued');

		expect(result.beforeDrain).toEqual({ out: ['start', 'started'], pending: 1 });
		expect(result.afterDrain.out).toEqual(['start', 'started', 'thread ran']);
		expect(result.result).toMatchObject({ ran: 1, pending: 0, drained: true, nextDue: -1 });
	});
});

describe('a sleep under a frozen clock', () => {
	it('completes with no wall time spent, and moves the clock the program reads', async () => {
		const result = await drive<{
			out: string[];
			result: DrainResult;
			wallElapsed: number;
			javaElapsed: number;
		}>('/sleeper/virtual');

		expect(result.out).toEqual(['before', 'returned', 'after sleep']);
		expect(result.result.drained).toBe(true);
		// two fibers: the thread's own start, then its resumption after the 50 ms sleep
		expect(result.result.ran).toBe(2);
		// the sleep was covered without waiting it out
		expect(result.wallElapsed).toBeLessThan(50);
		expect(result.javaElapsed).toBeGreaterThanOrEqual(50);
	});

	it('stops at the undue fiber under real time, then waits for it', async () => {
		const result = await drive<{
			out: string[];
			sync: DrainResult;
			async: DrainResult;
		}>('/sleeper/real');

		// the synchronous drain runs the thread's start and stops at the sleep it queued
		expect(result.sync).toMatchObject({ ran: 1, pending: 1, drained: false });
		expect(result.sync.nextDue).toBeGreaterThan(0);
		expect(result.async).toMatchObject({ ran: 1, pending: 0, drained: true });
		expect(result.out).toEqual(['before', 'returned', 'after sleep']);
	});
});
