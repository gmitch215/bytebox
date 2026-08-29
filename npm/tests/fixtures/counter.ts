import { DurableObject } from 'cloudflare:workers';
import type { Gate } from '../../src/gate.js';
import type { ByteboxModule } from '../../src/loader.js';

/**
 * The Durable Object class the plugin's scaffold writes, built here so both lanes share one copy.
 *
 * Every method forwards into an export keyed by the instance's own identifier, and goes through the
 * same gate a request does. It lives in a worker of its own because that is what the platform does:
 * a Durable Object runs in its own isolate, so it has its own heap and its own gate. Hosting it in
 * the calling Worker's script would put both on one heap, and a Worker waiting on its own Durable
 * Object would queue behind itself.
 */
export function counterClass(java: ByteboxModule, gate: Gate) {
	return class Counter extends DurableObject {
		private readonly key: string;

		constructor(ctx: DurableObjectState, env: Cloudflare.Env) {
			super(ctx, env);
			this.key = ctx.id.toString();
		}

		fetch(request: Request): Promise<Response> {
			return this.forward('durableCounterFetch', request) as Promise<Response>;
		}

		alarm(): Promise<void> {
			return this.forward('durableCounterAlarm') as Promise<void>;
		}

		webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
			// text and bytes take different exports, because the Java signatures differ
			const name =
				typeof message === 'string'
					? 'durableCounterMessageText'
					: 'durableCounterMessageBytes';
			return this.forward(name, socket, message) as Promise<void>;
		}

		webSocketClose(
			socket: WebSocket,
			code: number,
			reason: string,
			clean: boolean
		): Promise<void> {
			return this.forward(
				'durableCounterClosed',
				socket,
				code,
				reason,
				clean
			) as Promise<void>;
		}

		webSocketError(socket: WebSocket, error: unknown): Promise<void> {
			return this.forward('durableCounterFailed', socket, String(error)) as Promise<void>;
		}

		private forward(name: string, ...args: unknown[]): Promise<unknown> {
			return gate.run(() => {
				const answer = java.call(name, this.key, ...args, this.ctx, this.env);
				java.drain();
				return answer as Promise<unknown>;
			});
		}
	};
}
