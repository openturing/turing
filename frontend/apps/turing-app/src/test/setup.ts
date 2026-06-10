import "@testing-library/jest-dom/vitest";
import type { ReactNode } from "react";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

// ── RTL teardown ──
afterEach(() => {
  cleanup();
});

// Seed a readable CSRF cookie so the SDK's `ensureXsrfToken()` short-circuits
// (returns the cookie) instead of prefetching `/csrf` — keeps `fetch` call
// counts in the SSE tests equal to the number of chat turns.
document.cookie = "XSRF-TOKEN=test-csrf";

// ── jsdom gaps the chat UI relies on ──
// AgentChatTab auto-scrolls via endRef.scrollIntoView; jsdom has no impl.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = vi.fn();
}
// Radix primitives + next-themes probe these — jsdom ships neither.
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}
if (!globalThis.matchMedia) {
  globalThis.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof globalThis.matchMedia;
}

// ── Global module mocks (apply to every test file) ──
// i18n: passthrough `t` returning the key (or its defaultValue when present),
// so assertions can target stable strings without booting i18next.
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { defaultValue?: string }) => opts?.defaultValue ?? key,
    i18n: { language: "en", changeLanguage: () => Promise.resolve() },
  }),
  Trans: ({ children }: { children?: ReactNode }) => children,
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

// react-markdown pulls a large ESM tree (rehype/remark/micromark) that we
// don't need to exercise — render the raw text so assistant-bubble assertions
// stay simple. The chip buttons are rendered by ChatMessageList directly, so
// this mock doesn't affect the options assertions.
vi.mock("react-markdown", () => ({
  default: ({ children }: { children?: ReactNode }) => children,
  // TuringMarkdown (@viglet/turing-react-ui) imports this named export to
  // sanitize URLs after resolving the `sandbox:` scheme; the default mock above
  // never calls urlTransform, but provide it so the named import isn't undefined.
  defaultUrlTransform: (url: string) => url,
}));
vi.mock("remark-gfm", () => ({ default: () => undefined }));
vi.mock("rehype-highlight", () => ({ default: () => undefined }));

// useCurrentUser requires a UserProvider (which fetches the user over the
// network); stub it so the message list renders without that dependency.
vi.mock("@/contexts/user.context", () => ({
  useCurrentUser: () => ({
    user: { username: "tester", avatarUrl: null },
    refreshUser: () => {},
  }),
  UserProvider: ({ children }: { children?: ReactNode }) => children,
}));

// axios is the shared singleton both the admin services and the SDK's `get`
// helper import. Mock it once; tests set per-case implementations on these
// fns. `postAgentChat`/`postLlmChat` use `fetch` (not axios) — tests stub
// `global.fetch` for the SSE stream separately.
vi.mock("axios", () => {
  const mockAxios = {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} })),
    defaults: { baseURL: "" },
  };
  return { default: mockAxios };
});
