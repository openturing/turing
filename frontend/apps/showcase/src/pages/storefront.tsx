import { BrandHeader } from "@/components/brand-header";
import { ChatLauncher } from "@/components/chat/chat-launcher";
import { GuidedTour } from "@/components/guided-tour";
import { ProductCard } from "@/components/product-card";
import { Button } from "@/components/ui/button";
import { askAtlas } from "@/lib/ask-atlas";
import { firstValue } from "@/lib/format";
import { resolveSiteName } from "@/lib/resolve-site";
import { cn } from "@/lib/utils";
import {
  IconAdjustmentsHorizontal,
  IconArrowsSort,
  IconBolt,
  IconCamera,
  IconLoader2,
  IconMoodEmpty,
  IconSearch,
  IconSparkles,
  IconWand,
  IconX,
} from "@tabler/icons-react";
import {
  TuringProvider,
  TuringSearchField,
  useSearchStore,
  useTuringClickTracking,
  useTuringFacets,
  useTuringPagination,
  useTuringProactiveCopilot,
  useTuringSortOptions,
  useTuringSpellCheck,
  useTuringTabs,
  type ResolvedDocument,
} from "@viglet/turing-react-sdk";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

/* ── Ambient proactive copilot (T445) ── */
function ProactiveToast() {
  const { suggestion, dismiss } = useTuringProactiveCopilot();
  if (!suggestion) return null;
  return (
    <div className="fixed bottom-5 left-5 z-40 max-w-xs rounded-xl border border-border bg-card p-3 shadow-lg">
      <div className="flex items-start gap-2">
        <IconBolt className="mt-0.5 size-4 shrink-0 text-accent-foreground" />
        <div className="min-w-0 flex-1">
          <p className="text-sm text-foreground">{suggestion.message}</p>
          <div className="mt-2 flex gap-2">
            <Button
              size="sm"
              className="h-7 text-xs"
              onClick={() => {
                askAtlas(suggestion.suggestedPrompt);
                dismiss();
              }}
            >
              Ask Atlas
            </Button>
            <Button size="sm" variant="ghost" className="h-7 text-xs" onClick={dismiss}>
              Dismiss
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── Category tabs (T247) — drive the "category" facet from a top bar; the
   sidebar then hides the category group so the two don't conflict. ── */
const CATEGORY_TABS = [
  { label: "All" },
  { label: "Electronics", filter: "category:Electronics" },
  { label: "Home & Kitchen", filter: "category:Home & Kitchen" },
  { label: "Outdoors", filter: "category:Outdoors" },
  { label: "Fashion", filter: "category:Fashion" },
  { label: "Toys & Games", filter: "category:Toys & Games" },
] as const;

/* ── Facet sidebar (category hidden — tabs own it) ── */
function FacetSidebar() {
  const { facetGroups } = useTuringFacets();
  const groups = (facetGroups ?? []).filter((g) => g.name !== "category");
  if (groups.length === 0) return null;

  return (
    <aside className="space-y-5">
      <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-muted-foreground">
        <IconAdjustmentsHorizontal className="size-4" />
        Filters
      </div>
      {groups.map((group) => (
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

function SortSelect() {
  const store = useSearchStore();
  const { sortOptions } = useTuringSortOptions();
  if (!sortOptions || sortOptions.length === 0) return null;
  return (
    <label className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1.5 text-sm text-muted-foreground">
      <IconArrowsSort className="size-4" />
      <span className="sr-only">Sort by</span>
      <select
        value={store.params.sort ?? ""}
        onChange={(e) => store.setSort(e.target.value)}
        className="cursor-pointer bg-transparent pr-1 text-foreground focus:outline-none"
      >
        {sortOptions.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function SpellSuggestion() {
  const store = useSearchStore();
  const query = store.params.q ?? "*";
  const enabled = query !== "*" && query.trim().length > 0;
  const { suggestion } = useTuringSpellCheck({ query: enabled ? query : undefined, enabled });
  if (!suggestion || suggestion.toLowerCase() === query.toLowerCase()) return null;
  return (
    <div className="mb-4 flex items-center gap-2 rounded-lg border border-accent/40 bg-accent/10 px-3 py-2 text-sm">
      <IconWand className="size-4 text-accent-foreground" />
      <span className="text-muted-foreground">Did you mean</span>
      <button
        onClick={() => store.updateParams({ q: suggestion })}
        className="font-semibold text-primary underline-offset-2 hover:underline"
      >
        {suggestion}
      </button>
      <span className="text-muted-foreground">?</span>
    </div>
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
  const { tabs } = useTuringTabs({ attribute: "category", items: CATEGORY_TABS });
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

          {/* Search by image (T456) — hands off to the copilot's multimodal
              upload (vision → "find similar products"). */}
          <button
            type="button"
            onClick={() =>
              askAtlas("I'd like to find products similar to a photo — I'll attach an image.")
            }
            className="mx-auto mt-3 inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
          >
            <IconCamera className="size-4" />
            Search by image
          </button>
        </div>
      </section>

      {/* Category tabs */}
      <div className="border-b border-border/60">
        <div className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 sm:px-6">
          {tabs.map((tab) => (
            <button
              key={tab.index}
              onClick={tab.select}
              className={cn(
                "whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium transition-colors",
                tab.isActive
                  ? "border-primary text-primary"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

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
                    <button aria-label="Close filters" onClick={() => setMobileFiltersOpen(false)}>
                      <IconX className="size-5" />
                    </button>
                  </div>
                  <FacetSidebar />
                </div>
              </div>
            )}

            <div className="min-w-0 flex-1">
              <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
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
                  <SortSelect />
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

              <SpellSuggestion />

              {/* Glass-box ranking (T444) — ask the agent why the top result ranks #1. */}
              {hasResults && query !== "*" && (
                <button
                  type="button"
                  onClick={() =>
                    askAtlas(
                      `Why is "${firstValue(store.documents[0].raw.fields.title) || store.documents[0].title}" ranked first for "${query}"?`
                    )
                  }
                  className="mb-4 inline-flex items-center gap-1.5 rounded-full border border-primary/40 bg-primary/5 px-3 py-1 text-xs font-medium text-primary hover:bg-primary/10"
                >
                  <IconSparkles className="size-3.5" />
                  Why is the top result #1?
                </button>
              )}

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
          <span className="flex items-center gap-3">
            <a href="#/ops" className="hover:text-foreground">Ops tour</a>
            <a href="#/embed-demo" className="hover:text-foreground">Embed demo</a>
            <span>· {totalCount} products</span>
          </span>
        </div>
      </footer>

      <ProactiveToast />
      <ChatLauncher />
      <GuidedTour />
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
