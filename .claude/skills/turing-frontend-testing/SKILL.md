---
name: turing-frontend-testing
description: How to write frontend tests in the Turing turing-app React app — the vitest 3.2.4 + React Testing Library + jsdom + fake-indexeddb stack, the standalone vitest.config.ts (does NOT extend vite.config.ts), test file location and tsc type-checking constraints, the global mocks in src/test/setup.ts (react-i18next, react-markdown, axios, useCurrentUser), the fetch/SSE network boundary, full-page render wrappers, and IndexedDB setup. Use whenever adding or debugging a *.test.tsx under src/**/__tests__.
---

# Frontend Testing (vitest + React Testing Library)

The frontend gained its first test runner in 2026.3 (`turing-app` only).
Stack: **vitest 3.2.4 + @testing-library/react 16 + jsdom + fake-indexeddb**.

- **Run**: `pnpm --filter turing-app test` (CI) / `test:watch`. vitest 3.2.4
  runs fine against Vite 8 despite the peer-range warning; bump to vitest 4
  only if a Vite upgrade forces it.
- **Config**: standalone [`vitest.config.ts`](frontend/apps/turing-app/vitest.config.ts) —
  deliberately does **NOT** extend `vite.config.ts` (its Module Federation /
  Tailwind / viglet boot-loader plugins break under jsdom). Only the React
  plugin + `@` alias + `environment: "jsdom"`, `globals: true`, `css: false`,
  `clearMocks`/`restoreMocks: true`.
- **Test location**: `src/**/__tests__/*.test.tsx`. These ARE included by
  `tsconfig.app.json`, so `tsc -b` (the build) type-checks them — keep tests
  type-clean (`noUnusedLocals`/`Params`, `verbatimModuleSyntax` → `import type`).
  `src/test/vitest-globals.d.ts` provides `/// <reference types="vitest/globals" />`.
- **Global mocks** live in [`src/test/setup.ts`](frontend/apps/turing-app/src/test/setup.ts)
  (applied to every test): `react-i18next` (passthrough `t` returning the key
  or its `defaultValue`), `react-markdown` → raw children (skips the heavy
  rehype/remark ESM tree), `@/contexts/user.context` `useCurrentUser` stub, and
  the shared **`axios`** singleton (both admin services and the SDK's `get`
  helper import the default axios — one mock covers all reads). Plus jsdom
  polyfills: `scrollIntoView`, `ResizeObserver`, `matchMedia`.
- **Network boundary**: `postAgentChat`/`postLlmChat` use `fetch` (not axios) —
  stub `global.fetch` with an SSE `ReadableStream` of `data: {type:"token"|"options",content}`
  lines (see `chat/__tests__/test-utils.tsx` `makeSseFetch`). Slot polling and
  all other reads go through the mocked `axios.get`.
- **Full-page renders** (e.g. `ChatPage`) must be wrapped in a
  `QueryClientProvider` (the header's session-info sheet uses React Query) and a
  `MemoryRouter` — `renderWithRouter` in `test-utils.tsx` does both. Component-
  level renders (e.g. `AgentChatTab`) usually need neither.
- **IndexedDB**: `import "fake-indexeddb/auto"` at the top of the test file
  before anything touches the session store.
