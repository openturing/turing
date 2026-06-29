import { BrandHeader } from "@/components/brand-header";
import { ProductCard } from "@/components/product-card";
import { Button } from "@/components/ui/button";
import { resolveSiteName } from "@/lib/resolve-site";
import { cn } from "@/lib/utils";
import {
  IconAdjustmentsHorizontal,
  IconLoader2,
  IconMoodEmpty,
  IconSearch,
  IconSparkles,
  IconX,
} from "@tabler/icons-react";
import {
  TuringProvider,
  TuringSearchField,
  useSearchStore,
  useTuringClickTracking,
  useTuringFacets,
  useTuringPagination,
  type ResolvedDocument,
} from "@viglet/turing-react-sdk";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

/* ── Facet sidebar ── */
function FacetSidebar() {
  const { facetGroups } = useTuringFacets();
  if (!facetGroups || facetGroups.length === 0) return null;

  return (
    <aside className="space-y-5">
      <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground">
        <IconAdjustmentsHorizontal className="size-4" />
        Filters
      </div>
      {facetGroups.map((group) => (
        <div key={group.name} className="space-y-1.5">
          <h3 className="mb-1.5 text-[11px] font-bold uppercase tracking-wide text-foreground/70">
            {group.label}
          </h3>
          <div className="space-y-0.5">
            {group.facets.map((facet) => (
              <button
                key={facet.label + facet.link}
                onClick={facet.toggle}
                className={cn(
                  "flex w-full items-center justify-between rounded-md px-2.5 py-1.5 text-sm transition-all",
                  facet.selected
                    ? "bg-primary/10 font-medium text-primary"
                    : "text-muted-foreground hover:bg-accent/40 hover:text-foreground"
                )}
              >
                <span className="flex items-center gap-2 truncate">
                  {facet.selected && <IconX className="size-3 shrink-0 text-primary" />}
                  <span className="truncate text-[13px]">{facet.label}</span>
                </span>
                <span
                  className={cn(
                    "ml-2 shrink-0 rounded-full px-1.5 py-0.5 text-[10px]",
                    facet.selected
                      ? "bg-primary/15 text-primary"
                      : "bg-muted text-muted-foreground"
                  )}
                >
                  {facet.count}
                </span>
              </button>
            ))}
          </div>
        </div>
      ))}
    </aside>
  );
}

function Pagination() {
  const { pages, hasPages } = useTuringPagination();
  if (!hasPages) return null;
  return (
    <nav className="flex flex-wrap items-center justify-center gap-1 py-8">
      {pages.map((page, i) => (
        <Button
          key={`${page.type}-${page.label}-${i}`}
          variant={page.isCurrent ? "default" : "ghost"}
          size="sm"
          disabled={page.isCurrent || page.isEllipsis}
          onClick={page.select}
          className="min-w-9"
        >
          {page.label}
        </Button>
      ))}
    </nav>
  );
}

function resolveDocumentId(doc: ResolvedDocument): string {
  const fieldId = doc.raw.fields?.id;
  return doc.url || (fieldId != null ? String(fieldId) : "") || doc.title || "";
}

function StorefrontContent() {
  const navigate = useNavigate();
  const store = useSearchStore();
  const { totalCount } = useTuringPagination();
  const { trackClick } = useTuringClickTracking();
  const query = store.params.q ?? "*";
  const isLanding = query === "*" && store.status !== "loading";
  const hasResults = store.documents.length > 0;
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  function goToProduct(doc: ResolvedDocument, position: number) {
    trackClick(resolveDocumentId(doc), position);
    navigate("/product", { state: { document: doc } });
  }

  return (
    <div className="relative flex min-h-screen flex-col">
      <BrandHeader />

      {/* Hero */}
      <section
        className={cn(
          "relative atlas-sheen transition-all duration-500",
          isLanding ? "py-16 sm:py-24" : "py-8"
        )}
      >
        <div className="relative mx-auto max-w-3xl px-4 text-center sm:px-6">
          {isLanding && (
            <>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-border/60 bg-card px-3 py-1 text-xs text-muted-foreground">
                <IconSparkles className="size-3.5 text-primary" />
                Powered by Viglet Turing ES — search &amp; AI agents
              </div>
              <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl">
                Everything for the modern life,
                <span className="bg-linear-to-r from-primary to-indigo-500 bg-clip-text text-transparent">
                  {" "}
                  found instantly
                </span>
              </h1>
              <p className="mx-auto mt-3 max-w-xl text-sm text-muted-foreground sm:text-base">
                {totalCount > 0 ? (
                  <>
                    Search <span className="font-semibold text-foreground">{totalCount}</span>{" "}
                    products across electronics, home, outdoors, fashion and more.
                  </>
                ) : (
                  <>Search across electronics, home, outdoors, fashion and more.</>
                )}
              </p>
            </>
          )}

          <TuringSearchField
            turing={store}
            className={cn("relative z-50 mx-auto mt-6 w-full", isLanding ? "max-w-xl" : "max-w-2xl")}
          >
            <div className="relative">
              <div className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2 shadow-sm focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/20">
                <IconSearch className="size-5 shrink-0 text-muted-foreground" />
                <TuringSearchField.Input
                  placeholder="Search products… headphones, coffee maker, hiking boots…"
                  className="flex-1 border-0 bg-transparent text-base shadow-none placeholder:text-muted-foreground/60 focus-visible:outline-none focus-visible:ring-0"
                />
                <TuringSearchField.Button className="shrink-0 cursor-pointer rounded-md bg-primary px-4 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90">
                  Search
                </TuringSearchField.Button>
              </div>
              <TuringSearchField.Dropdown
                className="absolute z-50 mt-1.5 max-h-64 w-full overflow-y-auto overflow-hidden rounded-lg border border-border bg-popover shadow-xl"
                renderSuggestion={(term, onClick) => (
                  <button
                    key={term}
                    type="button"
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={onClick}
                    className="flex w-full cursor-pointer items-center gap-2 px-4 py-2.5 text-left text-sm hover:bg-accent"
                  >
                    <IconSearch className="size-3.5 shrink-0 text-muted-foreground" />
                    {term}
                  </button>
                )}
              />
            </div>
          </TuringSearchField>
        </div>
      </section>

      {/* Content */}
      <main className="mx-auto flex w-full max-w-7xl flex-1 gap-6 px-4 pb-12 sm:px-6 lg:gap-8">
        {store.status === "loading" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24">
            <IconLoader2 className="size-8 animate-spin text-primary" />
            <p className="text-sm text-muted-foreground">Searching the catalog…</p>
          </div>
        )}

        {store.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <p className="text-sm text-destructive">Search is unavailable. {store.error}</p>
            <Button variant="outline" size="sm" onClick={store.showAll}>
              Retry
            </Button>
          </div>
        )}

        {store.status === "success" && (
          <>
            <div className="hidden w-60 shrink-0 lg:block">
              <FacetSidebar />
            </div>

            {mobileFiltersOpen && (
              <div className="fixed inset-0 z-50 lg:hidden">
                <button
                  aria-label="Close filters"
                  className="fixed inset-0 bg-black/50 backdrop-blur-sm"
                  onClick={() => setMobileFiltersOpen(false)}
                />
                <div className="fixed inset-y-0 left-0 z-50 w-80 max-w-[85vw] overflow-y-auto border-r border-border bg-background p-4 shadow-2xl">
                  <div className="mb-4 flex items-center justify-between">
                    <span className="text-sm font-semibold">Filters</span>
                    <button onClick={() => setMobileFiltersOpen(false)}>
                      <IconX className="size-5" />
                    </button>
                  </div>
                  <FacetSidebar />
                </div>
              </div>
            )}

            <div className="min-w-0 flex-1">
              <div className="mb-5 flex items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">
                  <span className="font-semibold text-foreground">{totalCount}</span>{" "}
                  product{totalCount === 1 ? "" : "s"}
                  {query !== "*" && (
                    <>
                      {" "}
                      for <span className="font-medium text-foreground">“{query}”</span>
                    </>
                  )}
                </p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setMobileFiltersOpen(true)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-accent lg:hidden"
                  >
                    <IconAdjustmentsHorizontal className="size-4" />
                    Filters
                  </button>
                  {query !== "*" && (
                    <Button variant="ghost" size="sm" onClick={store.showAll} className="text-xs">
                      Clear
                    </Button>
                  )}
                </div>
              </div>

              {hasResults ? (
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                  {store.documents.map((doc, i) => (
                    <ProductCard
                      key={doc.raw.fields.id ?? `doc-${i}`}
                      doc={doc}
                      onClick={() => goToProduct(doc, i + 1)}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
                  <IconMoodEmpty className="size-10 text-muted-foreground/40" />
                  <p className="text-sm text-muted-foreground">No products match your search.</p>
                  <Button variant="outline" size="sm" onClick={store.showAll}>
                    Browse all products
                  </Button>
                </div>
              )}

              <Pagination />
            </div>
          </>
        )}
      </main>

      <footer className="border-t border-border/60 bg-card/40">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-2 px-4 py-6 text-xs text-muted-foreground sm:flex-row sm:px-6">
          <span>Atlas Store — a Viglet Turing ES reference showcase</span>
          <span>Sample data · {totalCount} products indexed</span>
        </div>
      </footer>
    </div>
  );
}

export default function StorefrontPage() {
  const site = resolveSiteName();
  const [searchParams, setSearchParams] = useSearchParams();
  return (
    <TuringProvider
      config={{ site, locale: import.meta.env.VITE_LOCALE }}
      urlSync={{ searchParams, setSearchParams: (s) => setSearchParams(s) }}
    >
      <StorefrontContent />
    </TuringProvider>
  );
}
