import { useTuringPagination } from "@viglet/turing-react-sdk";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export function SearchPagination() {
  const { t } = useTranslation();
  const { pages, currentPage, pageCount, hasPages } = useTuringPagination();

  if (!hasPages) return null;

  const prevPage = pages.find((p) => p.type === "PREVIOUS");
  const nextPage = pages.find((p) => p.type === "NEXT");
  const pageItems = pages.filter((p) =>
    p.type !== "PREVIOUS" && p.type !== "NEXT" && p.type !== "FIRST" && p.type !== "LAST",
  );

  return (
    <div className="mt-8 flex flex-col items-center gap-3">
      {/* Page info */}
      <p className="text-xs text-muted-foreground">
        {t("search.pageOf", { current: currentPage, total: pageCount })}
      </p>

      {/* Pagination bar */}
      <nav className="inline-flex items-center gap-1 rounded-xl border border-border/60 bg-muted/30 p-1.5" aria-label="Pagination">
        {/* Previous */}
        <button
          type="button"
          disabled={!prevPage || prevPage.isCurrent}
          onClick={() => prevPage?.select()}
          className="inline-flex items-center justify-center size-9 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
          aria-label="Previous page"
        >
          <IconChevronLeft className="size-4" />
        </button>

        {/* Page numbers */}
        {pageItems.map((page, i) => {
          if (page.isEllipsis) {
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
              onClick={page.select}
              disabled={page.isCurrent}
              aria-current={page.isCurrent ? "page" : undefined}
              className={`inline-flex items-center justify-center min-w-9 h-9 px-2 rounded-lg text-sm font-medium transition-all cursor-pointer ${page.isCurrent
                  ? "bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-md shadow-blue-500/25"
                  : "text-muted-foreground hover:text-foreground hover:bg-accent"
                }`}
            >
              {page.label}
            </button>
          );
        })}

        {/* Next */}
        <button
          type="button"
          disabled={!nextPage || nextPage.isCurrent}
          onClick={() => nextPage?.select()}
          className="inline-flex items-center justify-center size-9 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
          aria-label="Next page"
        >
          <IconChevronRight className="size-4" />
        </button>
      </nav>
    </div>
  );
}
