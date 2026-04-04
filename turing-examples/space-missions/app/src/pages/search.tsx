import { useMemo } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { resolveSiteName } from "@/lib/resolve-site";
import {
  TuringProvider,
  useTuringUrlSearch,
  useTuringAutoComplete,
  type ResolvedDocument,
  type TurFacetGroup,
  type TurFacetItem,
  type TurPaginationItem,
} from "@viglet/turing-react-sdk";
import {
  IconSearch,
  IconRocket,
  IconSatellite,
  IconUsers,
  IconCalendar,
  IconClock,
  IconFilter,
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconLoader2,
  IconMoodEmpty,
  IconX,
  IconPlanet,
  IconStars,
} from "@tabler/icons-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { ModeToggle } from "@/components/mode-toggle";
import { cn } from "@/lib/utils";

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
function Starfield() {
  const stars = useMemo(() => {
    const result: { id: number; x: number; y: number; size: number; duration: number; delay: number }[] = [];
    for (let i = 0; i < 80; i++) {
      result.push({
        id: i,
        x: Math.random() * 100,
        y: Math.random() * 100,
        size: Math.random() * 2 + 0.5,
        duration: Math.random() * 4 + 2,
        delay: Math.random() * 5,
      });
    }
    return result;
  }, []);

  return (
    <div className="starfield">
      {stars.map((s) => (
        <div
          key={s.id}
          className="star"
          style={{
            left: `${s.x}%`,
            top: `${s.y}%`,
            width: `${s.size}px`,
            height: `${s.size}px`,
            ["--twinkle-duration" as string]: `${s.duration}s`,
            ["--twinkle-delay" as string]: `${s.delay}s`,
          }}
        />
      ))}
    </div>
  );
}

/* ── Facet sidebar ── */
function FacetSidebar({
  groups,
  onNavigate,
}: {
  groups: TurFacetGroup[];
  onNavigate: (href: string) => void;
}) {
  if (!groups || groups.length === 0) return null;

  return (
    <aside className="w-64 shrink-0 space-y-5">
      <div className="flex items-center gap-2 text-xs font-bold text-primary uppercase tracking-[0.2em] font-mono">
        <IconFilter className="size-4" />
        Mission Filters
      </div>

      {groups.map((group) => (
        <div
          key={group.name}
          className="space-y-1.5 rounded-lg border border-border/40 bg-card/40 backdrop-blur-sm p-3"
        >
          <h3 className="text-[10px] font-bold uppercase tracking-[0.15em] text-primary/70 font-mono mb-2">
            {group.label?.text ?? group.name}
          </h3>
          <div className="space-y-0.5">
            {group.facets.map((facet: TurFacetItem) => (
              <button
                key={facet.label + facet.link}
                onClick={() => onNavigate(facet.link)}
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
}: {
  doc: ResolvedDocument;
  onClick: () => void;
}) {
  const fields = doc.raw.fields;
  const outcome = fields.outcome ?? "";
  const agency: string[] = Array.isArray(fields.agency)
    ? fields.agency
    : fields.agency
      ? [fields.agency]
      : [];
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
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-gradient-to-b from-primary/8 via-transparent to-transparent" />

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
          <div className="absolute inset-0 bg-gradient-to-t from-card via-card/30 to-transparent" />

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
function Pagination({
  items,
  onNavigate,
}: {
  items: TurPaginationItem[];
  onNavigate: (href: string) => void;
}) {
  if (!items || items.length === 0) return null;

  const iconMap: Record<string, React.ReactNode> = {
    FIRST: <IconChevronsLeft className="size-4" />,
    PREVIOUS: <IconChevronLeft className="size-4" />,
    NEXT: <IconChevronRight className="size-4" />,
    LAST: <IconChevronsRight className="size-4" />,
  };

  return (
    <nav className="flex items-center justify-center gap-1 py-8">
      {items.map((item, i) => {
        const isPage = item.type === "PAGE";
        const isCurrent = isPage && !item.href;

        return (
          <Button
            key={`${item.type}-${item.page}-${i}`}
            variant={isCurrent ? "default" : "ghost"}
            size="sm"
            disabled={!item.href}
            onClick={() => item.href && onNavigate(item.href)}
            className={cn(
              "min-w-8 font-mono",
              isCurrent && "pointer-events-none hud-glow"
            )}
          >
            {isPage ? item.text : iconMap[item.type] ?? item.text}
          </Button>
        );
      })}
    </nav>
  );
}

/* ── Main search content ── */
function SearchContent() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const turing = useTuringUrlSearch(searchParams, (s: string) =>
    setSearchParams(s)
  );
  const { suggestions, fetch: fetchSuggestions, clear: clearSuggestions } = useTuringAutoComplete(300);

  const query = turing.params.q ?? "*";
  const isLanding = query === "*" && turing.status !== "loading";
  const hasResults = turing.documents.length > 0;
  const facetGroups = turing.data?.widget?.facet ?? [];
  const pagination = turing.data?.pagination ?? [];
  const totalCount = turing.data?.queryContext?.count ?? 0;

  function goToDetail(doc: ResolvedDocument) {
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
            <span className="font-bold text-sm tracking-[0.1em] uppercase font-mono">
              Mission Control
            </span>
          </div>
          <ModeToggle />
        </div>
      </header>

      {/* ── Hero ── */}
      <section
        className={cn(
          "relative overflow-hidden transition-all duration-500",
          isLanding ? "py-20 sm:py-28" : "py-8 sm:py-10"
        )}
      >
        {/* Deep space gradient background */}
        <div className="absolute inset-0 bg-gradient-to-b from-[oklch(0.08_0.04_250)] via-background to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/15 via-transparent to-transparent" />

        {/* Starfield */}
        <Starfield />

        {/* Grid overlay for HUD effect */}
        <div
          className="absolute inset-0 opacity-[0.03] pointer-events-none"
          style={{
            backgroundImage:
              "linear-gradient(oklch(0.75 0.15 200 / 0.5) 1px, transparent 1px), linear-gradient(90deg, oklch(0.75 0.15 200 / 0.5) 1px, transparent 1px)",
            backgroundSize: "60px 60px",
          }}
        />

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

              <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tighter font-mono bg-gradient-to-b from-foreground via-foreground/90 to-muted-foreground bg-clip-text text-transparent pb-2 uppercase">
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
          <div
            className={cn(
              "mx-auto w-full transition-all duration-500",
              isLanding ? "mt-8 max-w-xl" : "max-w-2xl"
            )}
          >
            <div className="relative group">
              {/* Glow ring */}
              <div className="absolute -inset-0.5 rounded-xl bg-gradient-to-r from-primary/40 via-cyan-400/20 to-primary/40 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />

              <div className="relative flex items-center gap-2 rounded-xl border border-border/50 bg-card/70 backdrop-blur-md px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/50">
                <IconSearch className="size-5 text-primary/60 shrink-0" />
                <Input
                  value={turing.inputValue}
                  onChange={(e) => {
                    turing.setInputValue(e.target.value);
                    fetchSuggestions(e.target.value);
                  }}
                  onKeyDown={(e) => { if (e.key === "Enter") { clearSuggestions(); turing.submitSearch(); } }}
                  onBlur={() => globalThis.setTimeout(() => clearSuggestions(), 150)}
                  placeholder="Search missions... Apollo, Voyager, Mars..."
                  className="border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:border-transparent text-base placeholder:text-muted-foreground/50 font-mono"
                  autoComplete="off"
                />
                <Button
                  size="sm"
                  onClick={turing.submitSearch}
                  className="shrink-0 bg-gradient-to-r from-primary to-cyan-600 hover:from-primary/90 hover:to-cyan-600/90 text-primary-foreground shadow-md font-mono tracking-wider text-xs uppercase"
                >
                  Launch
                </Button>
              </div>

              {/* Autocomplete Dropdown */}
              {suggestions.length > 0 && (
                <div className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden">
                  {suggestions.map((term) => (
                    <button
                      key={term}
                      type="button"
                      className="w-full px-4 py-2.5 text-left text-sm hover:bg-accent transition-colors flex items-center gap-2 cursor-pointer font-mono"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => { turing.setInputValue(term); clearSuggestions(); }}
                    >
                      <IconSearch className="size-3.5 text-muted-foreground shrink-0" />
                      {term}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ── Content area ── */}
      <main className="mx-auto flex w-full max-w-7xl flex-1 gap-8 px-4 sm:px-6 pb-12">
        {/* Loading state */}
        {turing.status === "loading" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24">
            <IconLoader2 className="size-8 text-primary animate-spin" />
            <p className="text-sm text-muted-foreground font-mono">
              Scanning mission database...
            </p>
          </div>
        )}

        {/* Error state */}
        {turing.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconSatellite className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80 font-mono">
              Signal lost. Communication failure. {turing.error}
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={turing.showAll}
              className="font-mono"
            >
              Re-establish connection
            </Button>
          </div>
        )}

        {/* Results */}
        {turing.status === "success" && (
          <>
            {/* Facets sidebar */}
            <FacetSidebar
              groups={facetGroups}
              onNavigate={turing.navigate}
            />

            {/* Results area */}
            <div className="flex-1 min-w-0">
              {/* Results header */}
              {query !== "*" && (
                <div className="mb-6 flex items-center justify-between">
                  <p className="text-sm text-muted-foreground font-mono">
                    <span className="font-bold text-primary">
                      {totalCount}
                    </span>{" "}
                    mission{totalCount !== 1 ? "s" : ""} found
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
                    onClick={turing.showAll}
                    className="text-xs text-muted-foreground font-mono"
                  >
                    Clear search
                  </Button>
                </div>
              )}

              {/* Grid */}
              {hasResults ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {turing.documents.map((doc, i) => (
                    <MissionCard
                      key={doc.raw.fields.id ?? `doc-${i}`}
                      doc={doc}
                      onClick={() => goToDetail(doc)}
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
                    onClick={turing.showAll}
                    className="font-mono"
                  >
                    Show all missions
                  </Button>
                </div>
              )}

              {/* Pagination */}
              <Pagination items={pagination} onNavigate={turing.navigate} />
            </div>
          </>
        )}

        {/* Landing state - idle */}
        {turing.status === "idle" && (
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
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex items-center justify-between text-xs text-muted-foreground font-mono">
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
  return (
    <TuringProvider
      config={{
        site,
        locale: import.meta.env.VITE_LOCALE,
      }}
    >
      <SearchContent />
    </TuringProvider>
  );
}
