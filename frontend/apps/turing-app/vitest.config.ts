import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { playwright } from "@vitest/browser-playwright";
import { defineConfig } from "vitest/config";

// Standalone test config — deliberately does NOT extend vite.config.ts, whose
// Module Federation + viglet boot-loader plugins are irrelevant (and
// counter-productive) here. The `unit` project needs only the React transform
// and the `@` path alias; the `parity` project adds Tailwind, because the whole
// point of it is what the browser resolves out of this app's stylesheet chain.
//
// T1022 — the three vitest packages moved back to caret ranges. They were
// pinned to an exact 4.1.10 because 4.1.11 broke
// `@testing-library/jest-dom@7.0.0`'s module augmentation: every
// `toBeInTheDocument()` stopped type-checking with TS2339 while the tests
// themselves still passed, so it surfaced in `tsc -b` and nowhere else. The
// fault was jest-dom's `vitest.d.ts`, and 7.0.1 fixes it — which is why the
// floor here is `^7.0.1` and not `^7.0.0`: a fresh resolution landing on 7.0.0
// would bring the break back.
//
// Vitest still wants `@vitest/browser` on its own patch. That is a warning
// rather than a refusal, and a caret on all three moves them together.

const srcDir = fileURLToPath(new URL("./src", import.meta.url));
const alias = { "@": srcDir };

export default defineConfig({
  test: {
    projects: [
      {
        plugins: [react()],
        resolve: { alias },
        test: {
          name: "unit",
          globals: true,
          environment: "jsdom",
          setupFiles: ["./src/test/setup.ts"],
          include: ["src/**/*.test.{ts,tsx}"],
          // The render digest needs a browser to resolve a colour and a box;
          // jsdom hands back the class name and a zero rect.
          exclude: ["**/node_modules/**", "src/**/*.parity.test.tsx"],
          // CSS imports (chat-highlight.css, design-system styles) are no-ops
          // in this project.
          css: false,
          // Each test re-establishes its mock implementations; clear call
          // history + implementations between tests so fixtures don't leak
          // across files.
          clearMocks: true,
          restoreMocks: true,
        },
      },
      // T1001 — the bento layer now comes from a package, and nothing here
      // proved a page still renders the same afterwards. This project lays the
      // pages out in a real browser under this app's own stylesheet chain
      // (`src/index.css`: Tailwind v4 + the design-system preset + the shadcn
      // `@theme inline` block), which is the layer the package's own digest
      // cannot see because it does not exist there.
      //
      // No setupFiles: the unit setup mocks react-i18next, axios and
      // useCurrentUser, none of which a rendered surface needs, and one of
      // which (`css: false`) would defeat the whole project.
      {
        plugins: [react(), tailwindcss()],
        resolve: { alias },
        test: {
          name: "parity",
          include: ["src/**/*.parity.test.tsx"],
          browser: {
            enabled: true,
            headless: true,
            provider: playwright(),
            instances: [{ browser: "chromium" }],
          },
        },
      },
    ],
  },
});
