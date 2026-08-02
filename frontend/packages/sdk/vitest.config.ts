import { defineConfig } from "vitest/config";

/**
 * Standalone test config for `@viglet/turing-sdk` (T861).
 *
 * Deliberately separate from `vite.config.ts`: that file is the *library build*
 * (lib mode, ESM + UMD, `emptyOutDir`), and letting vitest inherit it means the
 * test run carries build-only options that have nothing to do with the suite.
 *
 * The package is zero-dependency and framework-agnostic, so there is no plugin
 * to wire — only the test surface itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
export default defineConfig({
  test: {
    include: ["src/**/*.test.ts"],
    clearMocks: true,
    restoreMocks: true,
  },
});
