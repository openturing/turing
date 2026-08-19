import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// T979 / §LXXXVIII — the showcase's first test config. Standalone on purpose,
// exactly like `apps/turing-app`'s: extending `vite.config.ts` would drag the
// Tailwind v4 plugin into jsdom, where it is irrelevant at best. The React
// transform and the `@` alias are the whole of what a render smoke needs.
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
    css: false,
    clearMocks: true,
    restoreMocks: true,
  },
});
