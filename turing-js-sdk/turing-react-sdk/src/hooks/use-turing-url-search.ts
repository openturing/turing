import { useCallback, useEffect, useRef, useState } from "react";
import { fetchChat, fetchSearch, parseHrefToParams, type SearchParams } from "../core/api";
import { useTuringContext } from "../core/use-turing-context";
import type {
  ResolvedDocument,
  ResolvedGroup,
  SearchStatus,
  TurChatResponse,
  TurSearchResponse,
} from "../core/types";

/**
 * Reads the browser URL search params and returns a SearchParams object.
 * Handles both `fq` and `fq[]` formats transparently.
 */
function urlToSearchParams(url: URLSearchParams, defaults: { locale?: string; sort?: string }): SearchParams {
  return {
    q: url.get("q") || "*",
    p: url.get("p") || "1",
    _setlocale: url.get("_setlocale") || defaults.locale,
    sort: url.get("sort") || defaults.sort || "relevance",
    fq: [...url.getAll("fq"), ...url.getAll("fq[]")].filter(Boolean) || undefined,
    tr: [...url.getAll("tr"), ...url.getAll("tr[]")].filter(Boolean) || undefined,
    nfpr: url.get("nfpr") || undefined,
    group: url.get("group") || undefined,
    rows: url.get("rows") || undefined,
  };
}

/**
 * Converts SearchParams back to a URLSearchParams string,
 * normalizing `%5B%5D` back to `[]`.
 */
function searchParamsToUrl(params: SearchParams): string {
  const url = new URLSearchParams();

  if (params.q && params.q !== "*") url.set("q", params.q);
  if (params.p && params.p !== "1") url.set("p", params.p);
  if (params._setlocale) url.set("_setlocale", params._setlocale);
  if (params.sort && params.sort !== "relevance") url.set("sort", params.sort);
  for (const v of params.fq ?? []) url.append("fq[]", v);
  for (const v of params.tr ?? []) url.append("tr[]", v);
  if (params.nfpr) url.set("nfpr", params.nfpr);
  if (params.group) url.set("group", params.group);
  if (params.rows) url.set("rows", params.rows);

  return url.toString().replaceAll("%5B%5D", "[]");
}

export interface UseTuringUrlSearchReturn {
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
  /** Current search params (derived from URL) */
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
}

function resolveDocuments(response: TurSearchResponse): ResolvedDocument[] {
  const df = response.queryContext.defaultFields;
  const docs = response.results?.document ??
    response.groups?.flatMap((g) => g.results?.document ?? []) ?? [];
  return docs.map((doc) => ({
    url: doc.fields[df.url] ?? "",
    title: doc.fields[df.title] ?? "",
    description: doc.fields[df.description] ?? "",
    date: doc.fields[df.date] ?? "",
    image: doc.fields[df.image] ?? "",
    text: doc.fields[df.text] ?? "",
    raw: doc,
  }));
}

function resolveGroups(response: TurSearchResponse): ResolvedGroup[] {
  if (!response.groups) return [];
  const df = response.queryContext.defaultFields;
  return response.groups.map((g) => ({
    name: g.name,
    count: g.count,
    page: g.page,
    pageCount: g.pageCount,
    limit: g.limit,
    pagination: g.pagination ?? [],
    documents: (g.results?.document ?? []).map((doc) => ({
      url: doc.fields[df.url] ?? "",
      title: doc.fields[df.title] ?? "",
      description: doc.fields[df.description] ?? "",
      date: doc.fields[df.date] ?? "",
      image: doc.fields[df.image] ?? "",
      text: doc.fields[df.text] ?? "",
      raw: doc,
    })),
  }));
}

/**
 * All-in-one search hook that automatically syncs with the browser URL.
 *
 * The URL is the single source of truth: any change to query params
 * triggers a search, and any search action updates the URL. This
 * eliminates all manual URL parsing and synchronization boilerplate.
 *
 * Requires `react-router-dom`'s `useSearchParams` to be passed in,
 * keeping the SDK router-agnostic.
 *
 * @example
 * ```tsx
 * function SearchPage() {
 *   const [searchParams, setSearchParams] = useSearchParams();
 *   const turing = useTuringUrlSearch(searchParams, setSearchParams);
 *
 *   return (
 *     <div>
 *       <input
 *         value={turing.inputValue}
 *         onChange={(e) => turing.setInputValue(e.target.value)}
 *         onKeyDown={(e) => e.key === "Enter" && turing.submitSearch()}
 *       />
 *       {turing.documents.map((doc) => <div key={doc.url}>{doc.title}</div>)}
 *       <button onClick={() => turing.setSort("newest")}>Sort by newest</button>
 *       <button onClick={() => turing.setLocale("pt_BR")}>Portugues</button>
 *     </div>
 *   );
 * }
 * ```
 *
 * @since 2026.2.0
 */
export function useTuringUrlSearch(
  searchParams: URLSearchParams,
  setSearchParams: (params: string) => void,
): UseTuringUrlSearchReturn {
  const { config } = useTuringContext();
  const abortRef = useRef(0);

  const [status, setStatus] = useState<SearchStatus>("idle");
  const [data, setData] = useState<TurSearchResponse | null>(null);
  const [chatData, setChatData] = useState<TurChatResponse | null>(null);
  const [documents, setDocuments] = useState<ResolvedDocument[]>([]);
  const [groups, setGroups] = useState<ResolvedGroup[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState(searchParams.get("q") || "");

  // Derive params from URL (source of truth)
  const params = urlToSearchParams(searchParams, {
    locale: config.locale,
    sort: config.sort,
  });

  // Execute search whenever URL changes
  useEffect(() => {
    const requestId = ++abortRef.current;
    setStatus("loading");
    setError(null);

    const q = params.q || "*";
    const shouldChat = q !== "*";

    Promise.all([
      fetchSearch(config.site, params),
      shouldChat
        ? fetchChat(config.site, { q, _setlocale: params._setlocale })
        : Promise.resolve(null),
    ])
      .then(([searchResult, chatResult]) => {
        if (requestId !== abortRef.current) return;
        setData(searchResult);
        setChatData(chatResult);
        setDocuments(resolveDocuments(searchResult));
        setGroups(resolveGroups(searchResult));
        setStatus("success");
      })
      .catch((err) => {
        if (requestId !== abortRef.current) return;
        setError(err instanceof Error ? err.message : "Search failed");
        setStatus("error");
      });
  }, [searchParams.toString()]);

  // ── URL mutation helpers ──

  const updateUrl = useCallback(
    (updater: (p: SearchParams) => SearchParams) => {
      const next = updater(params);
      setSearchParams(searchParamsToUrl(next));
    },
    [params, setSearchParams],
  );

  const navigate = useCallback(
    (href: string) => {
      const parsed = parseHrefToParams(href);
      setSearchParams(searchParamsToUrl(parsed));
    },
    [setSearchParams],
  );

  const submitSearch = useCallback(() => {
    updateUrl((p) => ({ ...p, q: inputValue || "*", p: "1" }));
  }, [inputValue, updateUrl]);

  const showAll = useCallback(() => {
    setInputValue("*");
    updateUrl((p) => ({ ...p, q: "*", p: "1" }));
  }, [updateUrl]);

  const setLocale = useCallback(
    (locale: string) => updateUrl((p) => ({ ...p, _setlocale: locale })),
    [updateUrl],
  );

  const setSort = useCallback(
    (sort: string) => updateUrl((p) => ({ ...p, sort })),
    [updateUrl],
  );

  return {
    status,
    data,
    chat: chatData,
    documents,
    groups,
    error,
    params,
    inputValue,
    setInputValue,
    submitSearch,
    navigate,
    setLocale,
    setSort,
    showAll,
  };
}
