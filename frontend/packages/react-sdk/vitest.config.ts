import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// T877 — standalone jsdom test config for the React SDK. The package ships via
// plain `tsc` and has no build-tool plugins of its own, so this only wires the
// React transform + jsdom needed to render the provider under test.
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
