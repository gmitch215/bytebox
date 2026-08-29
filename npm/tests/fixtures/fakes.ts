/**
 * Stand-ins for the platform services no local simulation provides.
 *
 * Workers AI, Vectorize, Workflows, Analytics Engine, rate limiting, the Secrets Store, Pipelines,
 * AI Search and Hyperdrive have no emulation: declaring one locally is either an error or a
 * connection to the real account. Each is an object carrying the methods the platform would, so the
 * Java half of the binding runs for real - the arguments it marshals, the promise it waits on, and
 * what it reads back - and only the service behind it is stood in for.
 *
 * These are values a worker holds, not miniflare configuration: a binding entry has to be
 * serialisable and a function is not.
 *
 * Sockets are deliberately absent. A TCP connection has a local equivalent - the mail server in
 * `docker/compose.yml` - so the socket route dials that rather than an object pretending to be one.
 */

const settled = <T>(value: T): Promise<T> => Promise.resolve(value);

function instance() {
	return {
		id: 'wf-000',
		status: () => settled({ status: 'running' }),
		pause: () => settled({}),
		resume: () => settled({}),
		terminate: () => settled({}),
		sendEvent: () => settled({})
	};
}

function transformer() {
	const chain = {
		transform: () => chain,
		output: () => settled({ image: () => 'transformed', contentType: () => 'image/webp' })
	};
	return chain;
}

/** Merged over the bindings miniflare does provide, keyed by the names the plugin assigns. */
export const bindings = {
	AI: { run: () => settled({ answer: 'a fixture answer' }) },
	VECTORIZE: {
		query: () => settled({ count: 2 }),
		insert: () => settled({ mutationId: 'inserted' }),
		upsert: () => settled({ mutationId: 'upserted' }),
		getByIds: () => settled([]),
		deleteByIds: () => settled({ mutationId: 'deleted' }),
		describe: () => settled({ dimensions: 768 })
	},
	WORKFLOW: {
		create: () => settled(instance()),
		get: () => settled(instance())
	},
	QUEUE: {
		send: () => settled({}),
		sendBatch: () => settled({})
	},
	ANALYTICS: { writeDataPoint: () => undefined },
	EMAIL: { send: () => settled({}) },
	RATELIMIT: { limit: () => settled({ success: true }) },
	SECRETS: { get: () => settled('a fixture secret') },
	PIPELINE: { send: () => settled({}) },
	IMAGES: {
		info: () => settled({ format: 'image/png', width: 4, height: 2 }),
		input: () => transformer()
	},
	// a container binding is named by the project rather than by a default, so this one is SIDECAR
	SIDECAR: {
		running: false,
		start: () => settled({}),
		signal: () => settled({}),
		getTcpPort: (port: number) => ({ port })
	},
	AI_SEARCH: {
		search: () => settled({ found: 'searched' }),
		aiSearch: () => settled({ found: 'asked' })
	},
	// the binding is stood in for; the database it names is the container in docker/compose.yml, so
	// a route that reads these details can open a real connection with them
	HYPERDRIVE: {
		connectionString: 'postgres://fixture:fixture@127.0.0.1:5432/bytebox',
		host: '127.0.0.1',
		port: 5432,
		user: 'fixture',
		password: 'fixture',
		database: 'bytebox'
	}
};
