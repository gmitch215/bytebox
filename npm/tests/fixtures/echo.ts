/**
 * A worker the fixture's outbound requests reach, so the HTTP surfaces can be driven with no
 * network. Registered as `outboundService`, which is what every `fetch()` from the fixture goes to,
 * and as a service binding, which is what `env.service()` returns.
 *
 * Written as a script rather than a bundled module because both lanes have to pass it through a
 * structured clone, and a string survives that where a function does not.
 */
export const ECHO_WORKER = `
	export default {
		async fetch(request) {
			const url = new URL(request.url);
			const status = url.pathname.match(/^\\/status\\/(\\d+)$/);
			if (status) {
				const code = Number(status[1]);
				// 204, 205 and 304 refuse a body, and constructing one with a body throws
				const body = code === 204 || code === 205 || code === 304
					? null
					: 'the server said ' + code;
				return new Response(body, { status: code });
			}
			if (url.pathname === '/boom') {
				throw new Error('the echo service refused');
			}
			if (url.pathname === '/redirect') {
				return Response.redirect(url.origin + '/echo', 302);
			}
			return Response.json(
				{
					method: request.method,
					path: url.pathname,
					query: url.search,
					fixture: request.headers.get('x-fixture'),
					body: await request.text()
				},
				{ headers: { 'x-echo': 'seen' } }
			);
		}
	};
`;
