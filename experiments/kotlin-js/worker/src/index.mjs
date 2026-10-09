import { handle } from './kotlin/bytebox-experiments-kotlin-js.mjs';

export default {
	fetch(request) {
		return handle(new URL(request.url).pathname);
	}
};
