import { createContext } from "react";
import type {
  TuringConfig,
  TurSearchResponse,
  TurChatResponse,
  TurStructuredFacet,
  TurStructuredFacetItem,
  TurStructuredSearchResponse,
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
} from "./types";
import type { SearchParams } from "./api";

// ── Configuration context (always available) ──

export interface TuringContextValue {
  config: TuringConfig;
}

export const TuringContext = createContext<TuringContextValue | null>(null);

// ── Search store context (available after first search) ──

export interface SearchStoreValue {
  /** Current search status */
  status: SearchStatus;
  /**
   * The legacy link-bearing response. Populated only when the provider runs in
   * GET mode (`urlSync.postNative: false`); `null` POST-native, where
   * {@link structured} is authoritative.
   */
  data: TurSearchResponse | null;
  /**
   * T710 — the link-free response, populated when the provider runs POST-native
   * (the default). Facets, pagination, locales and spell-check are data here,
   * never hrefs.
   */
  structured: TurStructuredSearchResponse | null;
  /** AI chat response */
  chat: TurChatResponse | null;
  /** Documents with resolved default fields */
  documents: ResolvedDocument[];
  /** Resolved groups (when `group` param is used) */
  groups: ResolvedGroup[];
  /** Error message */
  error: string | null;
  /**
   * Effective search params (explicit URL + implicit merged).
   * This is what gets sent to the API.
   */
  params: SearchParams;

  /** Current text input value (for controlled inputs) */
  inputValue: string;
  /** Update the text input value */
  setInputValue: (value: string) => void;
  /** Submit the current input as a search query */
  submitSearch: () => void;
  /**
   * Navigate via a Turing API href (facet, pagination, spell check links).
   *
   * @deprecated T710 / T713 — parsing a server-rendered URL back into params is
   * the query-string round-trip §XLV removes. Use {@link toggleFacet} /
   * {@link clearFacet} / {@link clearAllFilters} / {@link goToPage} /
   * {@link applySpellCheck}, which carry no URL. Logs a one-time console notice
   * and is **scheduled for removal in the next major**; as of T714 nothing in
   * this repository calls it. The links themselves stay — render them as real
   * anchors for deep-linking / SEO / no-JS.
   */
  navigate: (href: string) => void;
  /**
   * T710 — toggle a facet value from the structured response, applying the
   * item's own `action` (ADD / REMOVE / REPLACE) and resetting to page 1.
   */
  toggleFacet: (item: TurStructuredFacetItem) => void;
  /** T710 — clear every selection of one facet (replaces `cleanUpLink`). */
  clearFacet: (facet: TurStructuredFacet) => void;
  /** T710 — clear every filter (replaces `cleanUpFacets`). */
  clearAllFilters: () => void;
  /** T710 — go to a page by number (replaces a pagination `href`). */
  goToPage: (page: number) => void;
  /**
   * T710 — re-run with the spell-checker's suggestion, or force the original
   * term (`useOriginal: true`, the structured form of `nfpr=1`).
   */
  applySpellCheck: (useOriginal?: boolean) => void;
  /** Change locale */
  setLocale: (locale: string) => void;
  /** Change sort */
  setSort: (sort: string) => void;
  /** Reset to show all content (query = "*") */
  showAll: () => void;

  /**
   * Update multiple **explicit** (URL) params and trigger a new search.
   * Merges the partial into current URL params. Set a key to `undefined` to remove it.
   */
  updateParams: (partial: Partial<SearchParams>) => void;

  /**
   * Register implicit params under a unique key. Implicit params are merged
   * into every API request but do NOT appear in the URL.
   * Hooks like useTuringTabs use this to inject group/rows/sort automatically.
   *
   * @param key   Unique identifier (e.g. "tabs", "sort-defaults")
   * @param params  Partial search params to merge. Pass `null` to clear.
   */
  setImplicitParams: (key: string, params: Partial<SearchParams> | null) => void;
}

export const SearchStoreContext = createContext<SearchStoreValue | null>(null);
