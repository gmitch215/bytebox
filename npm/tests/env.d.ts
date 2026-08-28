/** The Vite plugin in `vite-wasm-bytes.ts`, for the node lane. */
declare module '*.wasm?bin' {
	const bytes: Uint8Array;
	export default bytes;
}

/** A wrangler `Data` module, for the Worker under test. */
declare module '*.wasm' {
	const bytes: ArrayBuffer;
	export default bytes;
}
