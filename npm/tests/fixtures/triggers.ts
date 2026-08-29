import type { Gate } from '../../src/gate.js';
import type { ByteboxModule } from '../../src/loader.js';

/**
 * The handlers the scaffold emits for every trigger other than `fetch`.
 *
 * Each forwards into the export the entry point declares for it, through the same gate a request
 * uses, because one heap serves the whole isolate whichever trigger arrived.
 */
export function triggerHandlers(java: ByteboxModule, gate: Gate, bindings: object) {
	const run = (name: string, ...args: unknown[]) =>
		gate.run(() => {
			const answer = java.call(name, ...args);
			java.drain();
			return answer as Promise<unknown>;
		});

	const handlers = {
		scheduled(controller: unknown, env: unknown, ctx: ExecutionContext) {
			return run('scheduled', controller, { ...(env as object), ...bindings }, ctx);
		},
		email(message: unknown, env: unknown, ctx: ExecutionContext) {
			return run('email', message, { ...(env as object), ...bindings }, ctx);
		},
		queue(batch: unknown, env: unknown, ctx: ExecutionContext) {
			return run('queue', batch, { ...(env as object), ...bindings }, ctx);
		},
		tail(events: unknown, env: unknown, ctx: ExecutionContext) {
			return run('tail', events, { ...(env as object), ...bindings }, ctx);
		},
		alarm(env: unknown, ctx: ExecutionContext) {
			return run('alarm', { ...(env as object), ...bindings }, ctx);
		}
	};

	/**
	 * Drives one trigger with an argument shaped the way the runtime shapes it.
	 *
	 * Only `scheduled` has a dispatcher the local runtime exposes, so the rest are called here. What
	 * that skips is workerd handing the argument over; what it keeps is every line between the export
	 * and the Java handler, which is the part with code in it.
	 */
	async function drive(name: string, env: unknown, ctx: ExecutionContext): Promise<Response> {
		switch (name) {
			case 'scheduled':
				await handlers.scheduled(
					{ cron: '*/5 * * * *', scheduledTime: 1_700_000_000_000, type: 'scheduled' },
					env,
					ctx
				);
				break;
			case 'email':
				await handlers.email(
					{
						from: 'sender@example.com',
						to: 'inbox@example.com',
						raw: new ReadableStream(),
						rawSize: 0,
						headers: new Headers(),
						forward: () => Promise.resolve(),
						reply: () => Promise.resolve(),
						setReject: () => undefined
					},
					env,
					ctx
				);
				break;
			case 'queue':
				await handlers.queue(
					{
						queue: 'fixture-queue',
						messages: [
							{
								id: 'm-1',
								timestamp: new Date(0),
								body: { n: 7 },
								attempts: 1,
								ack: () => undefined,
								retry: () => undefined
							}
						],
						ackAll: () => undefined,
						retryAll: () => undefined
					},
					env,
					ctx
				);
				break;
			case 'tail':
				await handlers.tail(
					[{ scriptName: 'fixture', outcome: 'ok', cpuTime: 1, wallTime: 2 }],
					env,
					ctx
				);
				break;
			case 'alarm':
				await handlers.alarm(env, ctx);
				break;
			default:
				return new Response(`no trigger named ${name}`, { status: 404 });
		}
		return new Response(`${name} ran`);
	}

	return { handlers, drive };
}
