import type { TurPaginationItem } from "@viglet/turing-react-sdk";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

function camelize(text: string): string {
  return text
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
}

interface SearchPaginationProps {
  items: TurPaginationItem[];
  currentPage: number;
  totalPages: number;
  onNavigate: (href: string) => void;
}

export function SearchPagination({ items, currentPage, totalPages, onNavigate }: Readonly<SearchPaginationProps>) {
  const { t } = useTranslation();

  if (!items.length || totalPages <= 1) return null;

  const prevItem = items.find((i) => i.type === "PREVIOUS");
  const nextItem = items.find((i) => i.type === "NEXT");
  const pageItems = items.filter((i) =>
    i.type !== "PREVIOUS" && i.type !== "NEXT" && i.type !== "FIRST" && i.type !== "LAST",
  );

  return (
    <div className="mt-8 flex flex-col items-center gap-3">
      {/* Page info */}
      <p className="text-xs text-muted-foreground">
        {t("search.pageOf", { current: currentPage, total: totalPages })}
      </p>

      {/* Pagination bar */}
      <nav className="inline-flex items-center gap-1 rounded-xl border border-border/60 bg-muted/30 p-1.5" aria-label="Pagination">
        {/* Previous */}
        <button
          type="button"
          disabled={!prevItem?.href}
          onClick={() => prevItem?.href && onNavigate(prevItem.href)}
          className="inline-flex items-center justify-center size-9 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
          aria-label="Previous page"
        >
          <IconChevronLeft className="size-4" />
        </button>

        {/* Page numbers */}
        {pageItems.map((page, i) => {
          const isEllipsis = page.type === "ELLIPSIS" || page.text === "...";
          const isCurrent = page.type === "CURRENT";

          if (isEllipsis) {
            return (
              <span key={`ellipsis-${i}`} className="inline-flex items-center justify-center size-9 text-muted-foreground text-sm" aria-hidden="true">
                &hellip;
              </span>
            );
          }

          return (
            <button
              key={`${page.type}-${page.page}-${i}`}
              type="button"
              onClick={(e) => {
                e.preventDefault();
                if (!isCurrent && page.href) onNavigate(page.href);
              }}
              disabled={isCurrent}
              aria-current={isCurrent ? "page" : undefined}
              className={`inline-flex items-center justify-center min-w-9 h-9 px-2 rounded-lg text-sm font-medium transition-all cursor-pointer ${
                isCurrent
                  ? "bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-md shadow-blue-500/25"
                  : "text-muted-foreground hover:text-foreground hover:bg-accent"
              }`}
            >
              {camelize(page.text)}
            </button>
          );
        })}

        {/* Next */}
        <button
          type="button"
          disabled={!nextItem?.href}
          onClick={() => nextItem?.href && onNavigate(nextItem.href)}
          className="inline-flex items-center justify-center size-9 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
          aria-label="Next page"
        >
          <IconChevronRight className="size-4" />
        </button>
      </nav>
    </div>
  );
}
