import { useCallback, useEffect, useRef, useState } from "react";
import { useTuringContext } from "../core/use-turing-context";

// ── Types ──

export interface SearchHistoryEntry {
  /** Auto-generated unique key */
  id?: number;
  /** SN site name */
  site: string;
  /** Origin domain (e.g. "localhost:5173") */
  domain: string;
  /** Search term */
  term: string;
  /** Timestamp of the search */
  timestamp: number;
}

export interface UseTuringSearchHistoryReturn {
  /** Recent search terms (newest first, deduplicated) */
  history: string[];
  /** Save a search term */
  save: (term: string) => void;
  /** Delete a specific term */
  remove: (term: string) => void;
  /** Delete all history for the current site + domain */
  clear: () => void;
}

// ── IndexedDB helpers ──

const DB_NAME = "turing_search_history";
const DB_VERSION = 1;
const STORE_NAME = "searches";

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, {
          keyPath: "id",
          autoIncrement: true,
        });
        store.createIndex("site_domain", ["site", "domain"], { unique: false });
        store.createIndex("site_domain_term", ["site", "domain", "term"], {
          unique: false,
        });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function getAllEntries(
  site: string,
  domain: string,
): Promise<SearchHistoryEntry[]> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, "readonly");
    const store = tx.objectStore(STORE_NAME);
    const index = store.index("site_domain");
    const request = index.getAll(IDBKeyRange.only([site, domain]));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function addEntry(entry: Omit<SearchHistoryEntry, "id">): Promise<void> {
  const db = await openDB();
  // Remove previous entry with same term (to update timestamp)
  const existing = await getEntriesByTerm(
    db,
    entry.site,
    entry.domain,
    entry.term,
  );
  const tx = db.transaction(STORE_NAME, "readwrite");
  const store = tx.objectStore(STORE_NAME);
  for (const old of existing) {
    if (old.id != null) store.delete(old.id);
  }
  store.add(entry);
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

function getEntriesByTerm(
  db: IDBDatabase,
  site: string,
  domain: string,
  term: string,
): Promise<SearchHistoryEntry[]> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, "readonly");
    const store = tx.objectStore(STORE_NAME);
    const index = store.index("site_domain_term");
    const request = index.getAll(IDBKeyRange.only([site, domain, term]));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function removeByTerm(
  site: string,
  domain: string,
  term: string,
): Promise<void> {
  const db = await openDB();
  const entries = await getEntriesByTerm(db, site, domain, term);
  if (entries.length === 0) return;
  const tx = db.transaction(STORE_NAME, "readwrite");
  const store = tx.objectStore(STORE_NAME);
  for (const entry of entries) {
    if (entry.id != null) store.delete(entry.id);
  }
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function clearAll(site: string, domain: string): Promise<void> {
  const db = await openDB();
  const entries = await getAllEntries(site, domain);
  if (entries.length === 0) return;
  const tx = db.transaction(STORE_NAME, "readwrite");
  const store = tx.objectStore(STORE_NAME);
  for (const entry of entries) {
    if (entry.id != null) store.delete(entry.id);
  }
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

// ── Hook ──

const MAX_HISTORY = 50;

/**
 * Persists search terms in the browser's IndexedDB, scoped by site name
 * and origin domain. Supports save, remove (single term), and clear (all).
 *
 * @param maxItems Maximum number of history entries to keep (default 50)
 *
 * @example
 * ```tsx
 * const { history, save, remove, clear } = useTuringSearchHistory();
 *
 * // Save when user submits search
 * function onSearch(term: string) {
 *   save(term);
 *   turing.submitSearch();
 * }
 *
 * // Show recent searches
 * history.map((term) => <div key={term}>{term}</div>)
 * ```
 *
 * @since 2026.2.4
 */
export function useTuringSearchHistory(
  maxItems: number = MAX_HISTORY,
): UseTuringSearchHistoryReturn {
  const { config } = useTuringContext();
  const site = config.site;
  const domain =
    typeof globalThis.location !== "undefined"
      ? globalThis.location.host
      : "unknown";

  const [history, setHistory] = useState<string[]>([]);
  const mountedRef = useRef(true);

  const refresh = useCallback(async () => {
    try {
      const entries = await getAllEntries(site, domain);
      // Sort newest first, deduplicate, limit
      const sorted = entries
        .sort((a, b) => b.timestamp - a.timestamp)
        .slice(0, maxItems);
      const terms = [...new Map(sorted.map((e) => [e.term, e])).keys()];
      if (mountedRef.current) setHistory(terms);
    } catch {
      // IndexedDB unavailable (SSR, private browsing, etc.)
    }
  }, [site, domain, maxItems]);

  useEffect(() => {
    mountedRef.current = true;
    refresh();
    return () => {
      mountedRef.current = false;
    };
  }, [refresh]);

  const save = useCallback(
    (term: string) => {
      const clean = term.trim();
      if (!clean || clean === "*") return;
      addEntry({ site, domain, term: clean, timestamp: Date.now() }).then(
        refresh,
      );
    },
    [site, domain, refresh],
  );

  const remove = useCallback(
    (term: string) => {
      removeByTerm(site, domain, term).then(refresh);
    },
    [site, domain, refresh],
  );

  const clearHistory = useCallback(() => {
    clearAll(site, domain).then(refresh);
  }, [site, domain, refresh]);

  return { history, save, remove, clear: clearHistory };
}
