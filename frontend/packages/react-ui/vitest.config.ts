import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Standalone jsdom test config for the headless UI package. react-ui has no
// build-tool plugins of its own (it ships via plain `tsc`), so this only wires
// the React transform + jsdom for render tests of the headless contract.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: false,
    clearMocks: true,
    restoreMocks: true,
  },
});
