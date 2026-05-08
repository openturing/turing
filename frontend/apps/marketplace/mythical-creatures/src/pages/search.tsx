import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { resolveSiteName } from "@/lib/resolve-site";
import { cn } from "@/lib/utils";
import {
  TuringProvider,
  TuringSearchField,
  useSearchStore,
  useTuringClickTracking,
  useTuringFacets,
  useTuringPagination,
  type ResolvedDocument,
} from "@viglet/turing-react-sdk";
import {
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconClock,
  IconFeather,
  IconFilter,
  IconFlame,
  IconLoader2,
  IconMoodEmpty,
  IconSearch,
  IconSkull,
  IconSparkles,
  IconX,
} from "@tabler/icons-react";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

/* ── Element color map ── */
const elementColors: Record<string, string> = {
  Fire: "bg-orange-500/20 text-orange-300 border-orange-500/30",
  Water: "bg-cyan-500/20 text-cyan-300 border-cyan-500/30",
  Earth: "bg-emerald-500/20 text-emerald-300 border-emerald-500/30",
  Air: "bg-sky-500/20 text-sky-300 border-sky-500/30",
  Lightning: "bg-yellow-500/20 text-yellow-300 border-yellow-500/30",
  Shadow: "bg-violet-500/20 text-violet-300 border-violet-500/30",
  Spirit: "bg-indigo-500/20 text-indigo-300 border-indigo-500/30",
  Ice: "bg-blue-300/20 text-blue-200 border-blue-300/30",
  Poison: "bg-lime-500/20 text-lime-300 border-lime-500/30",
};

function getElementColor(element: string): string {
  return elementColors[element] ?? "bg-purple-500/20 text-purple-300 border-purple-500/30";
}

/* ── Danger level bar ── */
function DangerBar({ level }: Readonly<{ level: number }>) {
  const safeLevel = Math.max(0, Math.min(Math.round(level), 10));
  const widthClassByLevel = [
    "w-0",
    "w-[10%]",
    "w-[20%]",
    "w-[30%]",
    "w-[40%]",
    "w-[50%]",
    "w-[60%]",
    "w-[70%]",
    "w-[80%]",
    "w-[90%]",
    "w-full",
  ];
  const widthClass = widthClassByLevel[safeLevel];
  let color = "from-emerald-600 to-emerald-400";

  if (level >= 8) {
    color = "from-red-600 to-red-400";
  } else if (level >= 5) {
    color = "from-amber-600 to-amber-400";
  }

  return (
    <div className="flex items-center gap-2">
      <IconSkull className="size-3.5 text-muted-foreground shrink-0" />
      <div className="h-1.5 flex-1 rounded-full bg-muted/50 overflow-hidden">
        <div className={cn("h-full rounded-full bg-linear-to-r", color, widthClass)} />
      </div>
      <span className="text-xs font-mono text-muted-foreground w-7 text-right">
        {level}
      </span>
    </div>
  );
}

/* ── Facet sidebar ── */
function FacetSidebar() {
  const { facetGroups } = useTuringFacets();
  if (!facetGroups || facetGroups.length === 0) return null;

  return (
    <aside className="w-64 shrink-0 space-y-6">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground/80 uppercase tracking-wider">
        <IconFilter className="size-4" />
        Filters
      </div>
      {facetGroups.map((group) => (
        <div key={group.name} className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {group.label}
          </h3>
          <div className="space-y-0.5">
            {group.facets.map((facet) => (
              <button
                key={facet.label + facet.link}
                onClick={facet.toggle}
                className={cn(
                  "flex w-full items-center justify-between rounded-md px-2.5 py-1.5 text-sm transition-colors",
                  facet.selected
                    ? "bg-primary/15 text-primary font-medium"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground"
                )}
              >
                <span className="flex items-center gap-2 truncate">
                  {facet.selected && (
                    <IconX className="size-3 shrink-0 text-primary" />
                  )}
                  <span className="truncate">{facet.label}</span>
                </span>
                <span
                  className={cn(
                    "ml-2 shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-mono",
                    facet.selected
                      ? "bg-primary/20 text-primary"
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

/* ── Creature card ── */
function CreatureCard({
  doc,
  onClick,
}: Readonly<{
  doc: ResolvedDocument;
  onClick: () => void;
}>) {
  const fields = doc.raw.fields;
  const dangerLevel = fields.danger_level ?? 0;

  const normalizeToStringArray = (value: unknown): string[] => {
    if (Array.isArray(value)) return value as string[];
    if (typeof value === "string" && value.length > 0) return [value];
    return [];
  };

  const elements: string[] = normalizeToStringArray(fields.element);
  const creatureTypes: string[] = normalizeToStringArray(fields.creature_type);

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/50 bg-card text-left transition-all duration-300 hover:border-primary/40 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {/* Glow effect on hover */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-linear-to-b from-primary/5 via-transparent to-transparent" />

      {/* Image */}
      {doc.image && (
        <div className="relative h-48 overflow-hidden bg-muted/30">
          <img
            src={doc.image}
            alt={doc.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-linear-to-t from-card via-transparent to-transparent" />

          {/* Danger badge overlay */}
          {dangerLevel > 0 && (
            <div className="absolute top-2 right-2 flex items-center gap-1 rounded-full bg-black/60 backdrop-blur-sm px-2 py-0.5 text-[10px] font-mono text-amber-400 border border-amber-500/30">
              <IconFlame className="size-3" />
              {dangerLevel}
            </div>
          )}
        </div>
      )}

      <div className="flex flex-1 flex-col gap-3 p-4">
        {/* Title */}
        <h3
          className="text-base font-semibold leading-tight line-clamp-2 group-hover:text-primary transition-colors"
          dangerouslySetInnerHTML={{ __html: fields.title ?? doc.title }}
        />

        {/* Description */}
        {doc.description && (
          <p className="text-sm text-muted-foreground line-clamp-3 leading-relaxed"
            dangerouslySetInnerHTML={{ __html: doc.description }}
          />
        )}

        {/* Tags */}
        <div className="flex flex-wrap gap-1.5 mt-auto pt-1">
          {creatureTypes.slice(0, 2).map((ct) => (
            <Badge
              key={ct}
              variant="secondary"
              className="text-[10px] py-0 h-5 bg-secondary/60"
            >
              <IconFeather className="size-2.5 mr-0.5" />
              {ct}
            </Badge>
          ))}
          {elements.slice(0, 2).map((el) => (
            <Badge
              key={el}
              variant="outline"
              className={cn("text-[10px] py-0 h-5", getElementColor(el))}
            >
              {el}
            </Badge>
          ))}
        </div>

        {/* Danger bar */}
        {dangerLevel > 0 && (
          <div className="mt-1">
            <DangerBar level={dangerLevel} />
          </div>
        )}
      </div>
    </button>
  );
}

/* ── Pagination ── */
function Pagination() {
  const { pages, hasPages } = useTuringPagination();
  if (!hasPages) return null;

  const iconMap: Record<string, React.ReactNode> = {
    FIRST: <IconChevronsLeft className="size-4" />,
    PREVIOUS: <IconChevronLeft className="size-4" />,
    NEXT: <IconChevronRight className="size-4" />,
    LAST: <IconChevronsRight className="size-4" />,
  };

  return (
    <nav className="flex flex-wrap items-center justify-center gap-1 py-8">
      {pages.map((page, i) => {
        const isPage = page.type === "PAGE";

        return (
          <Button
            key={`${page.type}-${page.label}-${i}`}
            variant={page.isCurrent ? "default" : "ghost"}
            size="sm"
            disabled={!page.isCurrent && !page.isEllipsis}
            onClick={page.select}
            className={cn(
              "min-w-8 font-mono",
              page.isCurrent && "pointer-events-none"
            )}
          >
            {isPage ? page.label : iconMap[page.type] ?? page.label}
          </Button>
        );
      })}
    </nav>
  );
}

/* ── Main search content ── */
function resolveDocumentId(doc: ResolvedDocument): string {
  const fieldId = doc.raw.fields?.id;
  return doc.url || (fieldId != null ? String(fieldId) : "") || doc.title || "";
}

function SearchContent() {
  const navigate = useNavigate();
  const store = useSearchStore();
  const { totalCount } = useTuringPagination();
  const { trackClick } = useTuringClickTracking();
  const query = store.params.q ?? "*";
  const isLanding = query === "*" && store.status !== "loading";
  const hasResults = store.documents.length > 0;
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  function goToDetail(doc: ResolvedDocument, position: number) {
    trackClick(resolveDocumentId(doc), position);
    navigate("/detail", { state: { document: doc } });
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/40 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2">
            <IconSparkles className="size-5 text-primary" />
            <span className="font-bold text-sm tracking-wide">
              The Bestiary
            </span>
          </div>
          <ModeToggle />
        </div>
      </header>

      {/* ── Hero ── */}
      <section
        className={cn(
          "relative overflow-x-clip transition-all duration-500",
          isLanding ? "py-24 sm:py-32" : "py-8 sm:py-10"
        )}
      >
        {/* Background effects */}
        <div className="absolute inset-0 bg-linear-to-b from-primary/8 via-background to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-primary/10 via-transparent to-transparent" />

        {/* Floating mystical particles - decorative */}
        <div className="absolute top-10 left-[15%] size-2 rounded-full bg-amber-400/20 animate-pulse" />
        <div className="absolute top-20 right-[20%] size-1.5 rounded-full bg-purple-400/30 animate-pulse [animation-delay:1s]" />
        <div className="absolute bottom-10 left-[40%] size-1 rounded-full bg-amber-300/25 animate-pulse [animation-delay:0.5s]" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 text-center">
          {isLanding && (
            <>
              <div className="mb-3 flex items-center justify-center gap-2 text-primary/70 text-sm font-medium uppercase tracking-widest">
                <Separator className="w-8 bg-primary/30" />
                Ancient Tome of
                <Separator className="w-8 bg-primary/30" />
              </div>
              <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight bg-linear-to-b from-foreground via-foreground to-muted-foreground bg-clip-text text-transparent pb-2">
                The Bestiary
              </h1>
              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-base sm:text-lg leading-relaxed">
                Explore mythical creatures from world folklore. Dragons, spirits,
                leviathans and forgotten beasts await within these pages.
              </p>
            </>
          )}

          {/* Search bar */}
          <TuringSearchField
            turing={store}
            className={cn(
              "relative z-50 mx-auto w-full transition-all duration-500",
              isLanding ? "mt-8 max-w-xl" : "max-w-2xl"
            )}
          >
            <div className="relative group">
              <div className="absolute -inset-0.5 rounded-xl bg-linear-to-r from-primary/30 via-amber-500/20 to-primary/30 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />
              <div className="relative flex items-center gap-2 rounded-xl border border-border/60 bg-card/80 backdrop-blur-sm px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/40">
                <IconSearch className="size-5 text-muted-foreground shrink-0" />
                <TuringSearchField.Input
                  placeholder="Search for a creature... dragon, phoenix, kraken..."
                  className="flex-1 border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:outline-none text-base placeholder:text-muted-foreground/60"
                />
                <TuringSearchField.Button className="shrink-0 inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium bg-linear-to-r from-primary to-purple-600 hover:from-primary/90 hover:to-purple-600/90 text-primary-foreground shadow-md cursor-pointer">
                  Search
                </TuringSearchField.Button>
              </div>
              <TuringSearchField.Dropdown
                className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden max-h-64 overflow-y-auto"
                renderSuggestion={(term, onClick) => (
                  <button key={term} type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClick}
                    className="w-full px-4 py-2.5 text-left text-sm hover:bg-accent transition-colors flex items-center gap-2 cursor-pointer">
                    <IconSearch className="size-3.5 text-muted-foreground shrink-0" />{term}
                  </button>
                )}
                renderHistory={(term, onClick, onRemove) => (
                  <div key={term} className="flex items-center hover:bg-accent transition-colors">
                    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClick}
                      className="flex-1 px-4 py-2.5 text-left text-sm flex items-center gap-2 cursor-pointer">
                      <IconClock className="size-3.5 text-muted-foreground shrink-0" />{term}
                    </button>
                    <button type="button" title="Remove" onMouseDown={(e) => e.preventDefault()} onClick={onRemove}
                      className="px-3 py-2.5 text-muted-foreground hover:text-destructive transition-colors cursor-pointer">&times;</button>
                  </div>
                )}
                renderHistoryHeader={(onClearAll) => (
                  <div className="flex items-center justify-between px-4 py-2 border-b border-border/50">
                    <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Recent searches</span>
                    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClearAll}
                      className="text-xs text-muted-foreground hover:text-destructive transition-colors cursor-pointer">Clear all</button>
                  </div>
                )}
              />
            </div>
          </TuringSearchField>
        </div>
      </section>

      {/* ── Content area ── */}
      <main className="mx-auto flex w-full max-w-7xl flex-1 gap-4 lg:gap-8 px-4 sm:px-6 pb-12">
        {/* Loading state */}
        {store.status === "loading" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24">
            <IconLoader2 className="size-8 text-primary animate-spin" />
            <p className="text-sm text-muted-foreground">
              Consulting the ancient scrolls...
            </p>
          </div>
        )}

        {/* Error state */}
        {store.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconSkull className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80">
              The tome could not be read. {store.error}
            </p>
            <Button variant="outline" size="sm" onClick={store.showAll}>
              Return to the Bestiary
            </Button>
          </div>
        )}

        {/* Results */}
        {store.status === "success" && (
          <>
            {/* Facets sidebar — desktop */}
            <div className="hidden lg:block w-64 shrink-0">
              <FacetSidebar />
            </div>

            {/* Mobile filter drawer */}
            {mobileFiltersOpen && (
              <div className="fixed inset-0 z-50 lg:hidden">
                <div
                  className="fixed inset-0 bg-black/60 backdrop-blur-sm"
                  onClick={() => setMobileFiltersOpen(false)}
                />
                <div className="fixed inset-y-0 left-0 z-50 w-80 max-w-[85vw] bg-background border-r border-border shadow-2xl overflow-y-auto">
                  <div className="sticky top-0 flex items-center justify-between p-4 border-b border-border/50 bg-background">
                    <span className="font-semibold text-sm flex items-center gap-2">
                      <IconFilter className="size-4" />
                      Filters
                    </span>
                    <button
                      onClick={() => setMobileFiltersOpen(false)}
                      className="p-1 rounded-md hover:bg-accent transition-colors"
                    >
                      <IconX className="size-5" />
                    </button>
                  </div>
                  <div className="p-4 [&>aside]:w-full">
                    <FacetSidebar />
                  </div>
                </div>
              </div>
            )}

            {/* Results area */}
            <div className="flex-1 min-w-0">
              {/* Mobile filter toggle */}
              <button
                onClick={() => setMobileFiltersOpen(true)}
                className="lg:hidden flex items-center gap-2 mb-4 px-3 py-2 rounded-lg border border-border/50 text-sm text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"
              >
                <IconFilter className="size-4" />
                Filters
              </button>

              {/* Results header */}
              {query !== "*" && (
                <div className="mb-6 flex items-center justify-between">
                  <p className="text-sm text-muted-foreground">
                    <span className="font-semibold text-foreground">
                      {totalCount}
                    </span>{" "}
                    creature{totalCount === 1 ? "" : "s"} found
                    {query !== "*" && (
                      <>
                        {" "}
                        for{" "}
                        <span className="font-medium text-primary">
                          &ldquo;{query}&rdquo;
                        </span>
                      </>
                    )}
                  </p>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={store.showAll}
                    className="text-xs text-muted-foreground"
                  >
                    Clear search
                  </Button>
                </div>
              )}

              {/* Grid */}
              {hasResults ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {store.documents.map((doc, i) => (
                    <CreatureCard
                      key={doc.raw.fields.id ?? `doc-${i}`}
                      doc={doc}
                      onClick={() => goToDetail(doc, i + 1)}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
                  <IconMoodEmpty className="size-10 text-muted-foreground/40" />
                  <p className="text-muted-foreground text-sm">
                    No creatures match your search. The bestiary pages are
                    blank.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={store.showAll}
                  >
                    Show all creatures
                  </Button>
                </div>
              )}

              {/* Pagination */}
              <Pagination />
            </div>
          </>
        )}

        {/* Landing state - no search yet, show all */}
        {store.status === "idle" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
            <IconSparkles className="size-10 text-primary/40" />
            <p className="text-muted-foreground text-sm max-w-md">
              Enter a search query or browse the full bestiary. Discover
              creatures from every age and origin.
            </p>
          </div>
        )}
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span>Mythical Creatures Bestiary</span>
        </div>
      </footer>
    </div>
  );
}

export default function SearchPage() {
  const site = resolveSiteName();
  const [searchParams, setSearchParams] = useSearchParams();
  return (
    <TuringProvider
      config={{ site, locale: import.meta.env.VITE_LOCALE }}
      urlSync={{ searchParams, setSearchParams: (s) => setSearchParams(s) }}
    >
      <SearchContent />
    </TuringProvider>
  );
}
