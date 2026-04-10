import { useMemo } from "react";
import { useSearchStore } from "../core/use-search-store";
import type { TurPaginationItem } from "../core/types";

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
  /** Raw pagination item from API */
  raw: TurPaginationItem;
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
  const rawItems = store.data?.pagination ?? [];
  const queryContext = store.data?.queryContext;
  const { navigate } = store;

  const pages = useMemo<PaginationPage[]>(() => {
    return rawItems.map((item) => {
      const isCurrent = item.type === "CURRENT";
      const isEllipsis = item.type === "ELLIPSIS" || item.text === "...";

      return {
        page: item.page,
        label: item.text,
        type: item.type,
        isCurrent,
        isEllipsis,
        select: () => {
          if (!isCurrent && !isEllipsis && item.href) {
            navigate(item.href);
          }
        },
        raw: item,
      };
    });
  }, [rawItems, navigate]);

  const currentPage = queryContext?.page ?? 1;
  const pageCount = queryContext?.pageCount ?? 1;
  const totalCount = queryContext?.count ?? 0;
  const hasPages = pages.length > 1;

  return { pages, currentPage, pageCount, totalCount, hasPages };
}
