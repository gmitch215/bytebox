/** The Vite plugin in `vite-wasm-bytes.ts`, for the node lane. */
declare module '*.wasm?bin' {
	const bytes: Uint8Array;
	export default bytes;
}

/** A text file read at build time by Vite, for `parse.spec.ts`. */
declare module '*.tsv?raw' {
	const text: string;
	export default text;
}

/** A wrangler `Data` module, for the Worker under test. */
declare module '*.wasm' {
	const bytes: ArrayBuffer;
	export default bytes;
}
