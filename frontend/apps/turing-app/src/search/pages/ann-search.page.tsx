import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  TurAnnSearchService,
  type TurAnnFacetItem,
  type TurAnnResultItem,
  type TurAnnSearchResponse,
} from "@/services/ann/ann.search.service";
import {
  IconAtom,
  IconBraces,
  IconChevronDown,
  IconChevronLeft,
  IconChevronRight,
  IconFilterX,
  IconLoader2,
  IconSearch,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useSearchParams } from "react-router-dom";
import { JsonSheet } from "../components/json-sheet";

const annSearchService = new TurAnnSearchService();

const DEFAULT_TOP_K = 20;
const DEFAULT_PAGE_SIZE = 20;

const RESERVED_PARAMS = new Set(["q", "_setlocale", "topK", "page", "pageSize"]);

type SelectedFilters = Record<string, string[]>;

export default function AnnSearchPage() {
  const { siteName } = useParams<{ siteName: string }>();
  const site = siteName || "";
  const { t } = useTranslation();

  const [searchParams, setSearchParams] = useSearchParams();
  const initialQuery = searchParams.get("q") || "";
  const initialLocale = searchParams.get("_setlocale") || "";
  const currentPage = Math.max(0, Number(searchParams.get("page") ?? 0) || 0);

  const [inputValue, setInputValue] = useState(initialQuery);
  const [response, setResponse] = useState<TurAnnSearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [jsonView, setJsonView] = useState<Record<string, unknown> | null>(null);
  const lastQueryRef = useRef<string>("");

  const selectedFilters = useMemo<SelectedFilters>(() => {
    const result: SelectedFilters = {};
    for (const [key, value] of searchParams.entries()) {
      if (RESERVED_PARAMS.has(key)) continue;
      if (!result[key]) result[key] = [];
      result[key].push(value);
    }
    return result;
  }, [searchParams]);

  const runSearch = useCallback(
    async (query: string, filters: SelectedFilters, locale: string, page: number) => {
      const isBrowseAll = !query.trim() || query.trim() === "*";
      setLoading(true);
      setError(null);
      try {
        const data = await annSearchService.search(site, {
          query: isBrowseAll ? "*" : query,
          locale: locale || undefined,
          topK: DEFAULT_TOP_K,
          page,
          pageSize: DEFAULT_PAGE_SIZE,
          filters: Object.keys(filters).length > 0 ? filters : undefined,
        });
        setResponse(data);
        lastQueryRef.current = query;
      } catch (e) {
        const status = (e as { response?: { status?: number } })?.response?.status;
        if (status === 403) {
          setError(t("ann.notAvailable"));
        } else if (status === 404) {
          setError(t("ann.siteNotFound"));
        } else {
          setError(t("ann.error"));
        }
        setResponse(null);
      } finally {
        setLoading(false);
      }
    },
    [site, t],
  );

  useEffect(() => {
    runSearch(initialQuery, selectedFilters, initialLocale, currentPage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const next = new URLSearchParams(searchParams);
    if (inputValue.trim()) {
      next.set("q", inputValue.trim());
    } else {
      next.delete("q");
    }
    next.delete("page");
    setSearchParams(next);
  };

  const toggleFilter = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    const current = next.getAll(key);
    next.delete(key);
    if (current.includes(value)) {
      current.filter((v) => v !== value).forEach((v) => next.append(key, v));
    } else {
      [...current, value].forEach((v) => next.append(key, v));
    }
    next.delete("page");
    setSearchParams(next);
  };

  const goToPage = (page: number) => {
    const next = new URLSearchParams(searchParams);
    if (page <= 0) {
      next.delete("page");
    } else {
      next.set("page", String(page));
    }
    setSearchParams(next);
  };

  const clearFilters = () => {
    const next = new URLSearchParams();
    const q = searchParams.get("q");
    const locale = searchParams.get("_setlocale");
    if (q) next.set("q", q);
    if (locale) next.set("_setlocale", locale);
    setSearchParams(next);
  };

  const hasFilters = Object.keys(selectedFilters).length > 0;
  const facets = response?.facets ?? {};

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <header className="border-b border-border/60 bg-background/95 backdrop-blur sticky top-0 z-10">
        <div className="container mx-auto px-4 lg:px-8 py-4">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center">
              <IconAtom className="size-5 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-semibold">{t("ann.title")}</h1>
              <p className="text-xs text-muted-foreground">{site}</p>
            </div>
          </div>
          <form onSubmit={handleSubmit} className="flex gap-2">
            <div className="relative flex-1">
              <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
              <Input
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={t("ann.placeholder")}
                className="pl-9"
              />
            </div>
            <Button type="submit" disabled={loading || !inputValue.trim()}>
              {loading ? <IconLoader2 className="size-4 animate-spin" /> : t("ann.search")}
            </Button>
          </form>
        </div>
      </header>

      <main className="flex-1 container mx-auto px-4 lg:px-8 py-6">
        {error && (
          <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive mb-4">
            {error}
          </div>
        )}

        {response && (
          <div className="flex gap-8">
            <aside className="w-64 shrink-0 space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold">{t("ann.facets")}</h2>
                {hasFilters && (
                  <Button variant="ghost" size="icon-sm" onClick={clearFilters} title={t("ann.clearFilters")}>
                    <IconFilterX className="size-4" />
                  </Button>
                )}
              </div>
              {Object.keys(facets).length === 0 ? (
                <p className="text-xs text-muted-foreground">{t("ann.noFacets")}</p>
              ) : (
                Object.entries(facets).map(([key, items]) => (
                  <FacetGroup
                    key={key}
                    facetKey={key}
                    items={items}
                    selected={selectedFilters[key] ?? []}
                    onToggle={toggleFilter}
                  />
                ))
              )}
            </aside>

            <section className="flex-1 min-w-0">
              <ResultsToolbar
                response={response}
                hasFilters={hasFilters}
                onClearFilters={clearFilters}
              />

              {response.results.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  <p>{t("ann.noResults")}</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {response.results.map((item) => (
                    <ResultCard
                      key={item.id}
                      item={item}
                      selectedFilters={selectedFilters}
                      onToggleFilter={toggleFilter}
                      onViewJson={() => setJsonView(item as unknown as Record<string, unknown>)}
                    />
                  ))}
                </div>
              )}

              <Pagination response={response} onGoToPage={goToPage} />
            </section>
          </div>
        )}
      </main>

      <JsonSheet data={jsonView} onClose={() => setJsonView(null)} />
    </div>
  );
}

interface FacetGroupProps {
  facetKey: string;
  items: TurAnnFacetItem[];
  selected: string[];
  onToggle: (key: string, value: string) => void;
}

function FacetGroup({ facetKey, items, selected, onToggle }: Readonly<FacetGroupProps>) {
  const [open, setOpen] = useState(true);
  if (items.length === 0) return null;
  return (
    <div className="border border-border/60 rounded-lg overflow-hidden">
      <button
        type="button"
        className="w-full flex items-center justify-between px-3 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground hover:bg-muted/40 transition-colors"
        onClick={() => setOpen(!open)}
      >
        <span className="truncate">{facetKey}</span>
        {open ? <IconChevronDown className="size-3.5" /> : <IconChevronRight className="size-3.5" />}
      </button>
      {open && (
        <ul className="px-3 pb-2 space-y-1 max-h-56 overflow-auto">
          {items.map((item) => {
            const isSelected = selected.includes(item.value);
            return (
              <li key={item.value}>
                <label className="flex items-center justify-between gap-2 text-sm cursor-pointer hover:bg-muted/30 rounded px-1.5 py-1">
                  <span className="flex items-center gap-2 min-w-0">
                    <input
                      type="checkbox"
                      className="accent-blue-600"
                      checked={isSelected}
                      onChange={() => onToggle(facetKey, item.value)}
                    />
                    <span className="truncate" title={item.value}>{item.value}</span>
                  </span>
                  <Badge variant="secondary" className="shrink-0 text-[10px]">{item.count}</Badge>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

interface ResultCardProps {
  item: TurAnnResultItem;
  selectedFilters: SelectedFilters;
  onToggleFilter: (key: string, value: string) => void;
  onViewJson: () => void;
}

function ResultCard({ item, selectedFilters, onToggleFilter, onViewJson }: Readonly<ResultCardProps>) {
  const { t } = useTranslation();
  const metaEntries = Object.entries(item.metadata ?? {});
  return (
    <article className="group rounded-xl border border-border/60 bg-card p-4 transition-all hover:border-blue-500/30 hover:shadow-lg hover:shadow-blue-500/5">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <IconAtom className="size-4 text-blue-500 shrink-0" />
          <span className="font-mono text-xs text-muted-foreground truncate">{item.id}</span>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {typeof item.score === "number" && (
            <Badge variant="secondary" className="text-[10px]">
              {t("ann.score")}: {item.score.toFixed(3)}
            </Badge>
          )}
          <Button variant="ghost" size="icon-sm" onClick={onViewJson} title={t("ann.viewJson")}>
            <IconBraces className="size-4" />
          </Button>
        </div>
      </div>
      {item.content && (
        <p className="text-sm leading-relaxed text-foreground/90 mb-3 whitespace-pre-wrap line-clamp-6">
          {item.content}
        </p>
      )}
      {metaEntries.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {metaEntries.flatMap(([key, value]) => {
            const values = expandMetaValues(value);
            if (values.length === 0) return [];
            return values.map((v) => {
              const isSelected = selectedFilters[key]?.includes(v) ?? false;
              return (
                <button
                  key={`${key}::${v}`}
                  type="button"
                  onClick={() => onToggleFilter(key, v)}
                  title={isSelected ? t("ann.removeFilter") : t("ann.applyFilter")}
                  className={
                    "inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-normal transition-colors cursor-pointer " +
                    (isSelected
                      ? "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300"
                      : "border-border/60 bg-transparent hover:border-blue-500/30 hover:bg-blue-500/5")
                  }
                >
                  <span className="text-muted-foreground">{key}:</span>
                  <span className="ml-1 truncate max-w-50">{v}</span>
                </button>
              );
            });
          })}
        </div>
      )}
    </article>
  );
}

function expandMetaValues(value: unknown): string[] {
  if (value === null || value === undefined) return [];
  if (Array.isArray(value)) {
    return value.filter((v) => v !== null && v !== undefined).map((v) => String(v));
  }
  if (typeof value === "object") return [JSON.stringify(value)];
  return [String(value)];
}

interface ResultsToolbarProps {
  response: TurAnnSearchResponse;
  hasFilters: boolean;
  onClearFilters: () => void;
}

function ResultsToolbar({ response, hasFilters, onClearFilters }: Readonly<ResultsToolbarProps>) {
  const { t } = useTranslation();
  const isBrowseAll = response.pageSize > 0 && response.topK === 0;
  const start = response.page * response.pageSize + 1;
  const shownEnd = response.page * response.pageSize + response.results.length;

  return (
    <div className="flex items-center justify-between mb-4 text-sm text-muted-foreground">
      <span>
        {isBrowseAll
          ? response.totalHits > 0
            ? t("ann.browseRange", {
                start: response.results.length === 0 ? 0 : start,
                end: shownEnd,
                total: response.totalHits,
              })
            : t("ann.browsePage", { page: response.page + 1, count: response.results.length })
          : t("ann.resultsCount", { count: response.totalHits, topK: response.topK })}
      </span>
      {hasFilters && (
        <button type="button" className="text-blue-600 hover:underline" onClick={onClearFilters}>
          {t("ann.clearFilters")}
        </button>
      )}
    </div>
  );
}

interface PaginationProps {
  response: TurAnnSearchResponse;
  onGoToPage: (page: number) => void;
}

function Pagination({ response, onGoToPage }: Readonly<PaginationProps>) {
  const { t } = useTranslation();
  const isBrowseAll = response.pageSize > 0 && response.topK === 0;
  if (!isBrowseAll) return null;

  const hasPrev = response.page > 0;
  const hasNext = response.hasMore;
  const totalPages = response.totalHits > 0 && response.pageSize > 0
    ? Math.ceil(response.totalHits / response.pageSize)
    : 0;

  if (!hasPrev && !hasNext) return null;

  return (
    <div className="flex items-center justify-center gap-2 pt-6 mt-2 border-t border-border/40">
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={!hasPrev}
        onClick={() => onGoToPage(response.page - 1)}
      >
        <IconChevronLeft className="size-4" />
        {t("ann.prev")}
      </Button>
      <span className="text-xs text-muted-foreground tabular-nums px-3">
        {totalPages > 0
          ? t("ann.pageOf", { current: response.page + 1, total: totalPages })
          : t("ann.pageNumber", { page: response.page + 1 })}
      </span>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={!hasNext}
        onClick={() => onGoToPage(response.page + 1)}
      >
        {t("ann.next")}
        <IconChevronRight className="size-4" />
      </Button>
    </div>
  );
}
