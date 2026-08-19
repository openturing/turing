import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

/**
 * T979 / §LXXXVIII — the showcase's test setup: the jsdom gaps this app's
 * dependencies probe, and nothing else.
 *
 * <p>Deliberately thinner than `apps/turing-app`'s. That one mocks i18n,
 * react-markdown, the user context and the shared axios singleton because its
 * tests reach across the whole admin. The showcase has one smoke, and it stubs
 * its own network per test — a global fixture here would decide what "the
 * storefront renders" means for every future test at once.
 */
afterEach(() => {
  cleanup();
});

// Radix primitives (dropdown, separator) and next-themes probe these; jsdom
// ships neither, and a missing one fails the render rather than the assertion.
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

if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = vi.fn();
}

// `ThemeProvider` reads `localStorage` unguarded at mount. Node prints
// "localStorage is not available because --localstorage-file was not provided"
// and leaves the global undefined, so without this the theme provider throws
// before any assertion runs. An in-memory shim keeps the read honest — a test
// that writes a theme can read it back — without a file on disk.
if (!globalThis.localStorage) {
  const store = new Map<string, string>();
  globalThis.localStorage = {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, String(value)),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => [...store.keys()][index] ?? null,
    get length() {
      return store.size;
    },
  } as Storage;
}
