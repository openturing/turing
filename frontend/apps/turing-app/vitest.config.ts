import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Standalone test config — deliberately does NOT extend vite.config.ts, whose
// Module Federation + Tailwind + viglet boot-loader plugins are irrelevant
// (and counter-productive) under jsdom. We only need the React transform and
// the `@` path alias.
const srcDir = fileURLToPath(new URL("./src", import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": srcDir,
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    // CSS imports (chat-highlight.css, design-system styles) are no-ops in tests.
    css: false,
    // Each test re-establishes its mock implementations; clear call history +
    // implementations between tests so fixtures don't leak across files.
    clearMocks: true,
    restoreMocks: true,
  },
});
