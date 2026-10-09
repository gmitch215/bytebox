import bytes from './kotlin.wasm';

const decoder = new TextDecoder();
const stdout = [];
let memory;

const wasi = {
	fd_write(fd, iovs, iovsLen, nwrittenPtr) {
		const view = new DataView(memory.buffer);
		let written = 0;
		for (let i = 0; i < iovsLen; i++) {
			const ptr = view.getUint32(iovs + i * 8, true);
			const len = view.getUint32(iovs + i * 8 + 4, true);
			if (fd === 1) stdout.push(decoder.decode(new Uint8Array(memory.buffer, ptr, len)));
			written += len;
		}
		view.setUint32(nwrittenPtr, written, true);
		return 0;
	},
	poll_oneoff: () => 52,
	proc_exit(code) {
		throw new Error('proc_exit ' + code);
	}
};

const module = new WebAssembly.Module(bytes);
const instance = new WebAssembly.Instance(module, { wasi_snapshot_preview1: wasi });
memory = instance.exports.memory;
instance.exports._start();

export default {
	fetch(request) {
		const n = new URL(request.url).pathname.length;
		return new Response(
			`${stdout.join('')}handle(${n}) = ${instance.exports.handle(n)}\nhandle(-1) = ${instance.exports.handle(-1)}\n`
		);
	}
};
