import { defineConfig } from 'vitest/config';

export default defineConfig({
	test: {
		include: ['tests/**/*.spec.ts'],
		environment: 'node',
		coverage: {
			// istanbul rather than v8: the workerd lane this grows into reports zero under the v8
			// provider, because that provider reads coverage off the node inspector
			provider: 'istanbul',
			reporter: ['text', 'json', 'lcov', 'clover'],
			reportsDirectory: './coverage',
			include: ['src/**/*.ts'],
			exclude: ['**/*.d.ts']
		}
	}
});
