import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  ResolvedDocument,
  TurPaginationItem,
  TurSortOption,
  TurSpellCheck,
  TurWidget,
} from "@viglet/turing-react-sdk";
import { IconList, IconSortDescending } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { FacetSidebar } from "./facet-sidebar";
import { ResultCard } from "./result-card";
import { SearchPagination } from "./search-pagination";
import { SpellCheckBanner } from "./spell-check-banner";

interface SearchResultsPanelProps {
  widget: TurWidget;
  documents: ResolvedDocument[];
  pagination: TurPaginationItem[];
  spellCheck: TurSpellCheck;
  sortOptions: TurSortOption[];
  currentSort: string;
  count: number;
  pageStart: number;
  pageEnd: number;
  pageCount: number;
  currentPage: number;
  isFacetItemTypeOr: boolean;
  onNavigate: (href: string) => void;
  onSortChange: (sort: string) => void;
  onViewJson: (doc: Record<string, unknown>) => void;
}

export function SearchResultsPanel({
  widget,
  documents,
  pagination,
  spellCheck,
  sortOptions,
  currentSort,
  count,
  pageStart,
  pageEnd,
  pageCount,
  currentPage,
  isFacetItemTypeOr,
  onNavigate,
  onSortChange,
  onViewJson,
}: Readonly<SearchResultsPanelProps>) {
  const { t } = useTranslation();

  return (
    <div className="flex gap-8">
      <FacetSidebar
        widget={widget}
        isFacetItemTypeOr={isFacetItemTypeOr}
        onNavigate={onNavigate}
      />

      <div className="flex-1 min-w-0">
        {/* Results toolbar */}
        <div className="flex flex-wrap items-center justify-between gap-3 mb-5 rounded-xl border border-border/60 bg-muted/30 px-5 py-3">
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center size-8 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 shrink-0">
              <IconList className="size-4 text-white" />
            </div>
            <div>
              <p
                className="text-sm"
                dangerouslySetInnerHTML={{
                  __html: t("search.resultsSummary", {
                    count: count.toLocaleString(),
                  }),
                }}
              />
              <p className="text-xs text-muted-foreground">
                {t("search.pageRange", { start: pageStart, end: pageEnd })}
                {pageCount > 1 && (
                  <>
                    <span className="mx-1.5 text-muted-foreground/40">&middot;</span>
                    {t("search.pageOf", { current: currentPage, total: pageCount })}
                  </>
                )}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <IconSortDescending className="size-4 text-muted-foreground" />
            <Select value={currentSort} onValueChange={onSortChange}>
              <SelectTrigger className="w-36 h-8 text-xs">
                <SelectValue placeholder={t("search.selectSort")} />
              </SelectTrigger>
              <SelectContent>
                {sortOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <SpellCheckBanner spellCheck={spellCheck} onNavigate={onNavigate} />

        {/* Document list */}
        <div className="space-y-3">
          {documents.map((doc, index) => (
            <ResultCard
              key={doc.url || index}
              document={doc}
              raw={doc.raw}
              onNavigate={onNavigate}
              onViewJson={onViewJson}
            />
          ))}
        </div>

        <SearchPagination
          items={pagination}
          currentPage={currentPage}
          totalPages={pageCount}
          onNavigate={onNavigate}
        />
      </div>
    </div>
  );
}
