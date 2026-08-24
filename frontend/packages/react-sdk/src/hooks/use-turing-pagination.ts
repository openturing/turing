import { useMemo } from "react";
import { useSearchStore } from "../core/use-search-store";
import type { TurPaginationItem, TurStructuredPage } from "../core/types";

// ── Types ──

export interface PaginationPage {
  /** Page number */
  page: number;
  /** Display label (e.g. "1", "2", "Next") */
  label: string;
  /** Item type from API: PAGE, CURRENT, FIRST, PREVIOUS, NEXT, LAST, ELLIPSIS */
  type: string;
  /** Whether this is the current active page */
  isCurrent: boolean;
  /** Whether this is an ellipsis placeholder */
  isEllipsis: boolean;
  /** Navigate to this page */
  select: () => void;
  /**
   * Raw pagination item from the API. POST-native (T710) this is the link-free
   * {@link TurStructuredPage} — same `page`/`text`/`type`, no `href`.
   */
  raw: TurPaginationItem | TurStructuredPage;
}

export interface UseTuringPaginationReturn {
  /** All pagination items with action helpers */
  pages: PaginationPage[];
  /** Current page number */
  currentPage: number;
  /** Total number of pages */
  pageCount: number;
  /** Total result count */
  totalCount: number;
  /** Whether there are multiple pages */
  hasPages: boolean;
}

// T1027 — one frozen empty array, shared. `?? []` builds a new array every
// render on a site that has none of whatever it fell back for, which gives the
// `useMemo` below it a dependency that changes each pass — so the memo runs
// every render and returns a new list, and so does everything downstream of it.
// A stable identity is the whole fix.
const EMPTY: never[] = [];

/**
 * Provides pagination data with action helpers.
 * Reads from the shared search store (requires `urlSync` on TuringProvider).
 *
 * @example
 * ```tsx
 * function Pagination() {
 *   const { pages, hasPages, currentPage, totalCount } = useTuringPagination();
 *
 *   if (!hasPages) return null;
 *
 *   return (
 *     <nav>
 *       <p>{totalCount} results - page {currentPage}</p>
 *       {pages.map((page, i) => (
 *         <button
 *           key={`${page.type}-${i}`}
 *           onClick={page.select}
 *           disabled={page.isCurrent || page.isEllipsis}
 *           aria-current={page.isCurrent ? "page" : undefined}
 *         >
 *           {page.label}
 *         </button>
 *       ))}
 *     </nav>
 *   );
 * }
 * ```
 *
 * @since 2026.3.0
 */
export function useTuringPagination(): UseTuringPaginationReturn {
  const store = useSearchStore();
  const structuredItems = store.structured?.pagination;
  // T710 — POST-native the pagination and counters come from the link-free
  // response; the legacy bean is the fallback.
  const rawItems: (TurPaginationItem | TurStructuredPage)[] =
    structuredItems ?? store.data?.pagination ?? EMPTY;
  const queryContext = store.structured?.queryContext ?? store.data?.queryContext;
  const { navigate, goToPage } = store;

  const pages = useMemo<PaginationPage[]>(() => {
    return rawItems.map((item) => {
      const isCurrent = ("current" in item && item.current) || item.type === "CURRENT";
      const isEllipsis = item.type === "ELLIPSIS" || item.text === "...";
      const href = "href" in item ? item.href : undefined;

      return {
        page: item.page,
        label: item.text,
        type: item.type,
        isCurrent,
        isEllipsis,
        select: () => {
          if (isCurrent || isEllipsis) return;
          // Page number first — no href to parse. The href path only runs for
          // the legacy response.
          if (!href) goToPage(item.page);
          else navigate(href);
        },
        raw: item,
      };
    });
  }, [rawItems, navigate, goToPage]);

  const currentPage = queryContext?.page ?? 1;
  const pageCount = queryContext?.pageCount ?? 1;
  const totalCount = queryContext?.count ?? 0;
  const hasPages = pages.length > 1;

  return { pages, currentPage, pageCount, totalCount, hasPages };
}
