import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { resolveSiteName } from "@/lib/resolve-site";
import { cn } from "@/lib/utils";
import "@/styles/search.css";
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
  IconCalendar,
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconClock,
  IconFilter,
  IconLoader2,
  IconMoodEmpty,
  IconPlanet,
  IconRocket,
  IconSatellite,
  IconSearch,
  IconStars,
  IconUsers,
  IconX,
} from "@tabler/icons-react";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

/* ── Outcome color map ── */
const outcomeColors: Record<string, string> = {
  Success: "bg-emerald-500/20 text-emerald-300 border-emerald-500/40",
  Failure: "bg-red-500/20 text-red-300 border-red-500/40",
  Partial: "bg-amber-500/20 text-amber-300 border-amber-500/40",
  "Partial Success": "bg-amber-500/20 text-amber-300 border-amber-500/40",
  Ongoing: "bg-cyan-500/20 text-cyan-300 border-cyan-500/40",
  Planned: "bg-indigo-500/20 text-indigo-300 border-indigo-500/40",
};

function getOutcomeColor(outcome: string): string {
  return (
    outcomeColors[outcome] ??
    "bg-slate-500/20 text-slate-300 border-slate-500/40"
  );
}

/* ── Agency color map ── */
const agencyColors: Record<string, string> = {
  NASA: "bg-blue-500/20 text-blue-300 border-blue-500/40",
  ESA: "bg-sky-500/20 text-sky-300 border-sky-500/40",
  Roscosmos: "bg-red-500/20 text-red-300 border-red-500/40",
  SpaceX: "bg-slate-500/20 text-slate-200 border-slate-500/40",
  JAXA: "bg-rose-500/20 text-rose-300 border-rose-500/40",
  ISRO: "bg-orange-500/20 text-orange-300 border-orange-500/40",
  CNSA: "bg-yellow-500/20 text-yellow-300 border-yellow-500/40",
};

function getAgencyColor(agency: string): string {
  return (
    agencyColors[agency] ??
    "bg-violet-500/20 text-violet-300 border-violet-500/40"
  );
}

/* ── Starfield background ── */
const STAR_CLASSES = [
  "left-[3%] top-[8%] w-[1px] h-[1px] [--twinkle-duration:2.7s] [--twinkle-delay:0.2s]",
  "left-[8%] top-[22%] w-[2px] h-[2px] [--twinkle-duration:4.1s] [--twinkle-delay:1.1s]",
  "left-[12%] top-[65%] w-[1.5px] h-[1.5px] [--twinkle-duration:3.4s] [--twinkle-delay:0.7s]",
  "left-[17%] top-[40%] w-[2px] h-[2px] [--twinkle-duration:5.5s] [--twinkle-delay:2.3s]",
  "left-[21%] top-[12%] w-[1px] h-[1px] [--twinkle-duration:2.9s] [--twinkle-delay:0.9s]",
  "left-[26%] top-[78%] w-[2px] h-[2px] [--twinkle-duration:4.8s] [--twinkle-delay:1.8s]",
  "left-[30%] top-[33%] w-[1.5px] h-[1.5px] [--twinkle-duration:3.2s] [--twinkle-delay:0.4s]",
  "left-[35%] top-[55%] w-[1px] h-[1px] [--twinkle-duration:5.1s] [--twinkle-delay:2.5s]",
  "left-[39%] top-[18%] w-[2px] h-[2px] [--twinkle-duration:2.6s] [--twinkle-delay:0.5s]",
  "left-[43%] top-[72%] w-[1.5px] h-[1.5px] [--twinkle-duration:4.5s] [--twinkle-delay:1.4s]",
  "left-[48%] top-[28%] w-[1px] h-[1px] [--twinkle-duration:3.7s] [--twinkle-delay:2.1s]",
  "left-[52%] top-[84%] w-[2px] h-[2px] [--twinkle-duration:5.8s] [--twinkle-delay:0.3s]",
  "left-[57%] top-[46%] w-[1.5px] h-[1.5px] [--twinkle-duration:3.1s] [--twinkle-delay:1.6s]",
  "left-[61%] top-[10%] w-[1px] h-[1px] [--twinkle-duration:4.9s] [--twinkle-delay:2.8s]",
  "left-[66%] top-[63%] w-[2px] h-[2px] [--twinkle-duration:2.8s] [--twinkle-delay:0.6s]",
  "left-[70%] top-[36%] w-[1.5px] h-[1.5px] [--twinkle-duration:5.2s] [--twinkle-delay:1.9s]",
  "left-[74%] top-[81%] w-[1px] h-[1px] [--twinkle-duration:3.5s] [--twinkle-delay:0.8s]",
  "left-[79%] top-[20%] w-[2px] h-[2px] [--twinkle-duration:4.3s] [--twinkle-delay:2.2s]",
  "left-[83%] top-[58%] w-[1.5px] h-[1.5px] [--twinkle-duration:3.9s] [--twinkle-delay:1.2s]",
  "left-[88%] top-[14%] w-[1px] h-[1px] [--twinkle-duration:5.6s] [--twinkle-delay:2.7s]",
  "left-[92%] top-[69%] w-[2px] h-[2px] [--twinkle-duration:2.5s] [--twinkle-delay:0.1s]",
  "left-[95%] top-[42%] w-[1.5px] h-[1.5px] [--twinkle-duration:4.7s] [--twinkle-delay:1.5s]",
  "left-[6%] top-[90%] w-[1px] h-[1px] [--twinkle-duration:3.3s] [--twinkle-delay:2.0s]",
  "left-[54%] top-[5%] w-[2px] h-[2px] [--twinkle-duration:5.0s] [--twinkle-delay:0.4s]",
] as const;

function Starfield() {
  return (
    <div className="starfield">
      {STAR_CLASSES.map((starClass) => (
        <div
          key={starClass}
          className={cn("star star-item", starClass)}
        />
      ))}
    </div>
  );
}

/* ── Facet sidebar ── */
function FacetSidebar() {
  const { facetGroups } = useTuringFacets();
  if (!facetGroups || facetGroups.length === 0) return null;

  return (
    <aside className="w-64 shrink-0 space-y-5">
      <div className="flex items-center gap-2 text-xs font-bold text-primary uppercase tracking-[0.2em] font-mono">
        <IconFilter className="size-4" />
        Mission Filters
      </div>

      {facetGroups.map((group) => (
        <div
          key={group.name}
          className="space-y-1.5 rounded-lg border border-border/40 bg-card/40 backdrop-blur-sm p-3"
        >
          <h3 className="text-[10px] font-bold uppercase tracking-[0.15em] text-primary/70 font-mono mb-2">
            {group.label}
          </h3>
          <div className="space-y-0.5">
            {group.facets.map((facet) => (
              <button
                key={facet.label + facet.link}
                onClick={facet.toggle}
                className={cn(
                  "flex w-full items-center justify-between rounded-md px-2.5 py-1.5 text-sm transition-all font-mono",
                  facet.selected
                    ? "bg-primary/15 text-primary font-medium border border-primary/30"
                    : "text-muted-foreground hover:bg-accent/50 hover:text-foreground"
                )}
              >
                <span className="flex items-center gap-2 truncate">
                  {facet.selected && (
                    <IconX className="size-3 shrink-0 text-primary" />
                  )}
                  <span className="truncate text-xs">{facet.label}</span>
                </span>
                <span
                  className={cn(
                    "ml-2 shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-mono",
                    facet.selected
                      ? "bg-primary/20 text-primary"
                      : "bg-muted/60 text-muted-foreground"
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

/* ── Mission card ── */
function MissionCard({
  doc,
  onClick,
}: Readonly<{
  doc: ResolvedDocument;
  onClick: () => void;
}>) {
  const fields = doc.raw.fields;
  const outcome = fields.outcome ?? "";
  let agency: string[] = [];
  if (Array.isArray(fields.agency)) {
    agency = fields.agency;
  } else if (fields.agency) {
    agency = [fields.agency];
  }
  const program = fields.program ?? "";
  const launchDate = fields.launch_date ?? "";
  const duration = fields.duration ?? "";
  const crewSize = fields.crew_size ?? 0;

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/40 bg-card/60 backdrop-blur-sm text-left transition-all duration-300 hover:border-primary/50 hover:shadow-lg hover:shadow-primary/10 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring scan-line"
    >
      {/* Glow effect on hover */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-linear-to-b from-primary/8 via-transparent to-transparent" />

      {/* HUD corner accents */}
      <div className="absolute top-0 left-0 w-4 h-4 border-t border-l border-primary/30 rounded-tl-xl pointer-events-none" />
      <div className="absolute top-0 right-0 w-4 h-4 border-t border-r border-primary/30 rounded-tr-xl pointer-events-none" />

      {/* Image */}
      {doc.image && (
        <div className="relative h-44 overflow-hidden bg-muted/20">
          <img
            src={doc.image}
            alt={doc.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-linear-to-t from-card via-card/30 to-transparent" />

          {/* Outcome badge overlay */}
          {outcome && (
            <div
              className={cn(
                "absolute top-2 right-2 rounded-full px-2.5 py-0.5 text-[10px] font-mono font-bold border backdrop-blur-sm",
                getOutcomeColor(outcome)
              )}
            >
              {outcome}
            </div>
          )}
        </div>
      )}

      <div className="flex flex-1 flex-col gap-2.5 p-4">
        {/* Title */}
        <h3
          className="text-sm font-bold leading-tight line-clamp-2 group-hover:text-primary transition-colors font-mono tracking-tight"
          dangerouslySetInnerHTML={{ __html: fields.title ?? doc.title }}
        />

        {/* Description */}
        {doc.description && (
          <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed"
            dangerouslySetInnerHTML={{ __html: doc.description }}
          />
        )}

        {/* Meta info row */}
        <div className="flex flex-wrap items-center gap-3 text-[10px] text-muted-foreground font-mono mt-auto pt-1">
          {launchDate && (
            <span className="flex items-center gap-1">
              <IconCalendar className="size-3" />
              {launchDate}
            </span>
          )}
          {duration && (
            <span className="flex items-center gap-1">
              <IconClock className="size-3" />
              {duration}
            </span>
          )}
          {crewSize > 0 && (
            <span className="flex items-center gap-1">
              <IconUsers className="size-3" />
              {crewSize}
            </span>
          )}
        </div>

        {/* Tags */}
        <div className="flex flex-wrap gap-1.5 pt-1">
          {agency.slice(0, 2).map((a) => (
            <Badge
              key={a}
              variant="outline"
              className={cn("text-[10px] py-0 h-5 font-mono", getAgencyColor(a))}
            >
              {a}
            </Badge>
          ))}
          {program && (
            <Badge
              variant="secondary"
              className="text-[10px] py-0 h-5 bg-secondary/60 font-mono"
            >
              <IconRocket className="size-2.5 mr-0.5" />
              {program}
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
              page.isCurrent && "pointer-events-none hud-glow"
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
    <div className="min-h-screen flex flex-col relative">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/30 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            <div className="relative">
              <IconRocket className="size-5 text-primary" />
              <div className="absolute -top-0.5 -right-0.5 size-1.5 rounded-full bg-primary animate-ping" />
            </div>
            <span className="font-bold text-sm tracking-widest uppercase font-mono">
              Mission Control
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
        {/* Deep space gradient background */}
        <div className="absolute inset-0 bg-linear-to-b from-[oklch(0.08_0.04_250)] via-background to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-primary/15 via-transparent to-transparent" />

        {/* Starfield */}
        <Starfield />

        {/* Grid overlay for HUD effect */}
        <div className="absolute inset-0 opacity-[0.03] pointer-events-none bg-[linear-gradient(oklch(0.75_0.15_200/0.5)_1px,transparent_1px),linear-gradient(90deg,oklch(0.75_0.15_200/0.5)_1px,transparent_1px)] bg-size-[60px_60px]" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 text-center">
          {isLanding && (
            <>
              <div className="mb-4 flex items-center justify-center gap-3 text-primary/60 text-xs font-mono uppercase tracking-[0.3em]">
                <Separator className="w-10 bg-primary/20" />
                <IconSatellite className="size-4" />
                Telemetry Online
                <IconSatellite className="size-4" />
                <Separator className="w-10 bg-primary/20" />
              </div>

              <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tighter font-mono bg-linear-to-b from-foreground via-foreground/90 to-muted-foreground bg-clip-text text-transparent pb-2 uppercase">
                Space Missions
              </h1>

              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-sm sm:text-base leading-relaxed">
                Explore humanity&apos;s greatest journeys beyond Earth. Search
                missions across agencies, programs, and decades of space
                exploration.
              </p>

              {/* Stats bar */}
              {totalCount > 0 && (
                <div className="mt-6 flex items-center justify-center gap-6 text-xs font-mono text-muted-foreground">
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-border/40 bg-card/30 backdrop-blur-sm">
                    <IconPlanet className="size-3.5 text-primary/70" />
                    <span>
                      <span className="text-primary font-bold">
                        {totalCount}
                      </span>{" "}
                      missions indexed
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-border/40 bg-card/30 backdrop-blur-sm">
                    <IconStars className="size-3.5 text-primary/70" />
                    <span>Database online</span>
                  </div>
                </div>
              )}
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
              <div className="absolute -inset-0.5 rounded-xl bg-linear-to-r from-primary/40 via-cyan-400/20 to-primary/40 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />
              <div className="relative flex items-center gap-2 rounded-xl border border-border/50 bg-card/70 backdrop-blur-md px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/50">
                <IconSearch className="size-5 text-primary/60 shrink-0" />
                <TuringSearchField.Input
                  placeholder="Search missions... Apollo, Voyager, Mars..."
                  className="flex-1 border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:outline-none text-base placeholder:text-muted-foreground/50 font-mono"
                />
                <TuringSearchField.Button className="shrink-0 inline-flex items-center justify-center rounded-md px-3 py-1.5 text-xs font-medium uppercase tracking-wider font-mono bg-linear-to-r from-primary to-cyan-600 hover:from-primary/90 hover:to-cyan-600/90 text-primary-foreground shadow-md cursor-pointer">
                  Launch
                </TuringSearchField.Button>
              </div>
              <TuringSearchField.Dropdown
                className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden max-h-64 overflow-y-auto"
                renderSuggestion={(term, onClick) => (
                  <button key={term} type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClick}
                    className="w-full px-4 py-2.5 text-left text-sm hover:bg-accent transition-colors flex items-center gap-2 cursor-pointer font-mono">
                    <IconSearch className="size-3.5 text-muted-foreground shrink-0" />{term}
                  </button>
                )}
                renderHistory={(term, onClick, onRemove) => (
                  <div key={term} className="flex items-center hover:bg-accent transition-colors">
                    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClick}
                      className="flex-1 px-4 py-2.5 text-left text-sm flex items-center gap-2 cursor-pointer font-mono">
                      <IconClock className="size-3.5 text-muted-foreground shrink-0" />{term}
                    </button>
                    <button type="button" title="Remove" onMouseDown={(e) => e.preventDefault()} onClick={onRemove}
                      className="px-3 py-2.5 text-muted-foreground hover:text-destructive transition-colors cursor-pointer">&times;</button>
                  </div>
                )}
                renderHistoryHeader={(onClearAll) => (
                  <div className="flex items-center justify-between px-4 py-2 border-b border-border/50">
                    <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider font-mono">Recent searches</span>
                    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClearAll}
                      className="text-xs text-muted-foreground hover:text-destructive transition-colors cursor-pointer font-mono">Clear all</button>
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
            <p className="text-sm text-muted-foreground font-mono">
              Scanning mission database...
            </p>
          </div>
        )}

        {/* Error state */}
        {store.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconSatellite className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80 font-mono">
              Signal lost. Communication failure. {store.error}
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={store.showAll}
              className="font-mono"
            >
              Re-establish connection
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
                    <span className="font-semibold text-sm flex items-center gap-2 font-mono">
                      <IconFilter className="size-4" />
                      Mission Filters
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
                className="lg:hidden flex items-center gap-2 mb-4 px-3 py-2 rounded-lg border border-border/50 text-sm text-muted-foreground hover:text-foreground hover:bg-accent transition-colors font-mono"
              >
                <IconFilter className="size-4" />
                Filters
              </button>

              {/* Results header */}
              {query !== "*" && (
                <div className="mb-6 flex items-center justify-between">
                  <p className="text-sm text-muted-foreground font-mono">
                    <span className="font-bold text-primary">
                      {totalCount}
                    </span>{" "}
                    mission{totalCount === 1 ? "" : "s"} found
                    {query !== "*" && (
                      <>
                        {" "}
                        for{" "}
                        <span className="font-medium text-foreground">
                          &ldquo;{query}&rdquo;
                        </span>
                      </>
                    )}
                  </p>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={store.showAll}
                    className="text-xs text-muted-foreground font-mono"
                  >
                    Clear search
                  </Button>
                </div>
              )}

              {/* Grid */}
              {hasResults ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {store.documents.map((doc, i) => (
                    <MissionCard
                      key={doc.raw.fields.id ?? `doc-${i}`}
                      doc={doc}
                      onClick={() => goToDetail(doc, i + 1)}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
                  <IconMoodEmpty className="size-10 text-muted-foreground/40" />
                  <p className="text-muted-foreground text-sm font-mono">
                    No missions found in this sector.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={store.showAll}
                    className="font-mono"
                  >
                    Show all missions
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
            <IconRocket className="size-10 text-primary/40" />
            <p className="text-muted-foreground text-sm max-w-md font-mono">
              Enter a search query or browse the full mission archive. Discover
              every launch, orbit, and landing.
            </p>
          </div>
        )}
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/20 bg-card/20 backdrop-blur-sm">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground font-mono">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconSatellite className="size-3" />
            Space Missions Database
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
      config={{
        site,
        locale: import.meta.env.VITE_LOCALE,
      }}
      urlSync={{ searchParams, setSearchParams: (s) => setSearchParams(s) }}
    >
      <SearchContent />
    </TuringProvider>
  );
}
