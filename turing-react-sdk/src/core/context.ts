import { createContext } from "react";
import type { TuringConfig, TurSearchResponse, TurChatResponse, ResolvedDocument, ResolvedGroup, SearchStatus } from "./types";
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
  /** Raw API response */
  data: TurSearchResponse | null;
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
  /** Navigate via a Turing API href (facet, pagination, spell check links) */
  navigate: (href: string) => void;
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
