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
  IconCertificate,
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconClock,
  IconDisc,
  IconFilter,
  IconLoader2,
  IconMoodEmpty,
  IconMusic,
  IconSearch,
  IconStar,
  IconStarFilled,
  IconTag,
  IconVinyl,
  IconX,
} from "@tabler/icons-react";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

/* ── Condition color map ── */
const conditionColors: Record<string, string> = {
  Mint: "bg-emerald-500/20 text-emerald-300 border-emerald-500/30",
  "Near Mint": "bg-emerald-500/15 text-emerald-400 border-emerald-500/25",
  "Very Good Plus": "bg-blue-500/20 text-blue-300 border-blue-500/30",
  "Very Good": "bg-yellow-500/20 text-yellow-300 border-yellow-500/30",
  "Good Plus": "bg-orange-500/20 text-orange-300 border-orange-500/30",
  Good: "bg-orange-500/15 text-orange-400 border-orange-500/25",
  Fair: "bg-red-500/15 text-red-400 border-red-500/25",
};

function getConditionColor(condition: string): string {
  return (
    conditionColors[condition] ??
    "bg-amber-500/20 text-amber-300 border-amber-500/30"
  );
}

/* ── Star rating display ── */
function StarRating({ rating }: Readonly<{ rating: number }>) {
  const stars = Math.round(rating);
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: 5 }, (_, i) =>
        i < stars ? (
          <IconStarFilled
            key={i}
            className="size-3.5 text-amber-400"
          />
        ) : (
          <IconStar key={i} className="size-3.5 text-muted-foreground/30" />
        )
      )}
      <span className="ml-1 text-xs text-muted-foreground font-mono">
        {rating}
      </span>
    </div>
  );
}

/* ── Spinning vinyl record SVG ── */
function VinylRecord({ className }: Readonly<{ className?: string }>) {
  return (
    <div className={cn("vinyl-spin", className)}>
      <svg
        viewBox="0 0 200 200"
        xmlns="http://www.w3.org/2000/svg"
        className="w-full h-full"
      >
        {/* Outer disc */}
        <circle cx="100" cy="100" r="98" fill="#1a1a1a" stroke="#333" strokeWidth="1" />
        {/* Grooves */}
        <circle cx="100" cy="100" r="90" fill="none" stroke="#2a2a2a" strokeWidth="0.5" />
        <circle cx="100" cy="100" r="82" fill="none" stroke="#222" strokeWidth="0.5" />
        <circle cx="100" cy="100" r="74" fill="none" stroke="#2a2a2a" strokeWidth="0.5" />
        <circle cx="100" cy="100" r="66" fill="none" stroke="#222" strokeWidth="0.5" />
        <circle cx="100" cy="100" r="58" fill="none" stroke="#2a2a2a" strokeWidth="0.5" />
        <circle cx="100" cy="100" r="50" fill="none" stroke="#222" strokeWidth="0.5" />
        <circle cx="100" cy="100" r="42" fill="none" stroke="#2a2a2a" strokeWidth="0.5" />
        {/* Label area */}
        <circle cx="100" cy="100" r="34" fill="#d97706" />
        <circle cx="100" cy="100" r="33" fill="url(#labelGradient)" />
        {/* Center hole */}
        <circle cx="100" cy="100" r="5" fill="#1a1a1a" />
        {/* Light reflection */}
        <ellipse cx="70" cy="70" rx="40" ry="20" fill="white" opacity="0.03" transform="rotate(-30 70 70)" />
        {/* Gradient definition */}
        <defs>
          <radialGradient id="labelGradient">
            <stop offset="0%" stopColor="#f59e0b" />
            <stop offset="100%" stopColor="#d97706" />
          </radialGradient>
        </defs>
      </svg>
    </div>
  );
}

/* ── Facet sidebar ── */
function FacetSidebar() {
  const { facetGroups } = useTuringFacets();

  if (!facetGroups || facetGroups.length === 0) return null;

  return (
    <aside className="w-64 shrink-0 space-y-6">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground/80 uppercase tracking-widest">
        <IconFilter className="size-4" />
        Browse By
      </div>
      {facetGroups.map((group) => (
        <div key={group.name} className="space-y-2">
          <h3 className="text-xs font-bold uppercase tracking-widest text-primary/70 border-b border-primary/20 pb-1.5">
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

/* ── Album card ── */
function AlbumCard({
  doc,
  onClick,
}: Readonly<{
  doc: ResolvedDocument;
  onClick: () => void;
}>) {
  const fields = doc.raw.fields;
  const coverArt = fields.cover_art ?? doc.image;
  const artist = fields.artist ?? "";
  const genres: string[] = [];
  if (Array.isArray(fields.genre)) {
    genres.push(...fields.genre);
  } else if (fields.genre) {
    genres.push(fields.genre);
  }
  const condition = fields.condition ?? "";
  const format = fields.format ?? "";
  const price = fields.price;
  const rating = fields.rating ?? 0;
  const isFirstPressing =
    fields.is_first_pressing === true || fields.is_first_pressing === "true";

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/50 bg-card text-left transition-all duration-300 hover:border-primary/40 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {/* Glow effect on hover */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-linear-to-b from-primary/5 via-transparent to-transparent" />

      {/* Cover art */}
      <div className="relative aspect-square overflow-hidden bg-muted/30">
        {coverArt ? (
          <img
            src={coverArt}
            alt={doc.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-linear-to-br from-amber-900/20 to-orange-900/20">
            <IconDisc className="size-16 text-muted-foreground/20" />
          </div>
        )}
        <div className="absolute inset-0 bg-linear-to-t from-card via-transparent to-transparent" />

        {/* Price badge overlay */}
        {price != null && (
          <div className="absolute top-2 right-2 rounded-full bg-black/70 backdrop-blur-sm px-2.5 py-0.5 text-xs font-bold text-amber-300 border border-amber-500/30">
            ${price}
          </div>
        )}

        {/* First pressing badge */}
        {isFirstPressing && (
          <div className="absolute top-2 left-2 flex items-center gap-1 rounded-full bg-amber-600/90 backdrop-blur-sm px-2 py-0.5 text-[10px] font-bold text-white">
            <IconCertificate className="size-3" />
            1st Press
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-2 p-4">
        {/* Title */}
        <h3
          className="text-base font-semibold leading-tight line-clamp-2 group-hover:text-primary transition-colors"
          dangerouslySetInnerHTML={{ __html: fields.title ?? doc.title }}
        />

        {/* Artist */}
        {artist && (
          <p className="text-sm text-muted-foreground flex items-center gap-1.5">
            <IconMusic className="size-3.5 shrink-0" />
            <span className="truncate">{artist}</span>
          </p>
        )}

        {/* Rating */}
        {rating > 0 && <StarRating rating={Number(rating)} />}

        {/* Tags */}
        <div className="flex flex-wrap gap-1.5 mt-auto pt-1">
          {genres.slice(0, 2).map((g) => (
            <Badge
              key={g}
              variant="secondary"
              className="text-[10px] py-0 h-5 bg-secondary/60"
            >
              <IconTag className="size-2.5 mr-0.5" />
              {g}
            </Badge>
          ))}
          {condition && (
            <Badge
              variant="outline"
              className={cn("text-[10px] py-0 h-5", getConditionColor(condition))}
            >
              {condition}
            </Badge>
          )}
          {format && (
            <Badge
              variant="outline"
              className="text-[10px] py-0 h-5 bg-purple-500/10 text-purple-300 border-purple-500/20"
            >
              {format}
            </Badge>
          )}
        </div>
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
            disabled={page.isCurrent || page.isEllipsis}
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

function resolveDocumentId(doc: ResolvedDocument): string {
  const fieldId = doc.raw.fields?.id;
  return doc.url || (fieldId != null ? String(fieldId) : "") || doc.title || "";
}

/* ── Main search content ── */
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
          <div className="flex items-center gap-2.5">
            <IconVinyl className="size-6 text-primary" />
            <span className="font-bold text-sm tracking-wide uppercase">
              The Crate
            </span>
          </div>
          <ModeToggle />
        </div>
      </header>

      {/* ── Hero ── */}
      <section
        className={cn(
          "relative overflow-x-clip transition-all duration-500",
          isLanding ? "py-20 sm:py-28" : "py-8 sm:py-10"
        )}
      >
        {/* Warm gradient background */}
        <div className="absolute inset-0 bg-linear-to-b from-primary/10 via-amber-600/5 to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-orange-500/8 via-transparent to-transparent" />

        {/* Decorative warm particles */}
        <div className="absolute top-8 left-[12%] size-2 rounded-full bg-amber-400/20 animate-pulse" />
        <div className="absolute top-16 right-[18%] size-1.5 rounded-full bg-orange-400/25 animate-pulse [animation-delay:1s]" />
        <div className="absolute bottom-12 left-[35%] size-1 rounded-full bg-amber-300/20 animate-pulse [animation-delay:0.5s]" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 text-center">
          {isLanding && (
            <>
              {/* Spinning vinyl record */}
              <div className="mx-auto mb-6 w-32 h-32 sm:w-40 sm:h-40 opacity-80">
                <VinylRecord />
              </div>

              <div className="mb-3 flex items-center justify-center gap-2 text-primary/70 text-sm font-medium uppercase tracking-widest">
                <Separator className="w-8 bg-primary/30" />
                Welcome to
                <Separator className="w-8 bg-primary/30" />
              </div>
              <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight bg-linear-to-b from-foreground via-foreground to-muted-foreground bg-clip-text text-transparent pb-2 uppercase">
                The Crate
              </h1>
              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-base sm:text-lg leading-relaxed">
                Dig through the world&apos;s finest vinyl collection.
                {totalCount > 0 && (
                  <span className="block mt-1 text-primary/80 font-medium">
                    {totalCount} records in the stacks
                  </span>
                )}
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
              <div className="absolute -inset-0.5 rounded-xl bg-linear-to-r from-amber-500/30 via-orange-500/20 to-amber-500/30 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />
              <div className="relative flex items-center gap-2 rounded-xl border border-border/60 bg-card/80 backdrop-blur-sm px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/40">
                <IconSearch className="size-5 text-muted-foreground shrink-0" />
                <TuringSearchField.Input
                  placeholder="Search for an album... Miles Davis, punk, 1970s..."
                  className="flex-1 border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:outline-none text-base placeholder:text-muted-foreground/60"
                />
                <TuringSearchField.Button className="shrink-0 inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium bg-linear-to-r from-amber-600 to-orange-600 hover:from-amber-500 hover:to-orange-500 text-white shadow-md cursor-pointer">
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
              Flipping through the crates...
            </p>
          </div>
        )}

        {/* Error state */}
        {store.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconDisc className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80">
              The turntable hit a scratch. {store.error}
            </p>
            <Button variant="outline" size="sm" onClick={store.showAll}>
              Back to the Crate
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
                      Browse By
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
                    record{totalCount === 1 ? "" : "s"} found
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
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                  {store.documents.map((doc, i) => (
                    <AlbumCard
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
                    Nothing in this section. Try another genre?
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={store.showAll}
                  >
                    Show all records
                  </Button>
                </div>
              )}

              {/* Pagination */}
              <Pagination />
            </div>
          </>
        )}

        {/* Landing state - idle */}
        {store.status === "idle" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
            <IconVinyl className="size-10 text-primary/40" />
            <p className="text-muted-foreground text-sm max-w-md">
              Enter a search query or browse the full collection. Every record
              has a story to tell.
            </p>
          </div>
        )}
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconVinyl className="size-3.5" />
            The Crate — Vinyl Records
          </span>
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
