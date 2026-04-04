import { useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { resolveSiteName } from "@/lib/resolve-site";
import {
  TuringProvider,
  useTuringUrlSearch,
  useTuringAutoComplete,
  useTuringSearchHistory,
  type ResolvedDocument,
  type TurFacetGroup,
  type TurFacetItem,
  type TurPaginationItem,
} from "@viglet/turing-react-sdk";
import {
  IconSearch,
  IconFlame,
  IconSkull,
  IconFeather,
  IconSparkles,
  IconFilter,
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconLoader2,
  IconMoodEmpty,
  IconX,
  IconClock,
  IconTrash,
} from "@tabler/icons-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { ModeToggle } from "@/components/mode-toggle";
import { cn } from "@/lib/utils";

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
function DangerBar({ level }: { level: number }) {
  const percent = Math.min((level / 10) * 100, 100);
  const color =
    level >= 8
      ? "from-red-600 to-red-400"
      : level >= 5
        ? "from-amber-600 to-amber-400"
        : "from-emerald-600 to-emerald-400";

  return (
    <div className="flex items-center gap-2">
      <IconSkull className="size-3.5 text-muted-foreground shrink-0" />
      <div className="h-1.5 flex-1 rounded-full bg-muted/50 overflow-hidden">
        <div
          className={cn("h-full rounded-full bg-gradient-to-r", color)}
          style={{ width: `${percent}%` }}
        />
      </div>
      <span className="text-xs font-mono text-muted-foreground w-7 text-right">
        {level}
      </span>
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
    <aside className="w-64 shrink-0 space-y-6">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground/80 uppercase tracking-wider">
        <IconFilter className="size-4" />
        Filters
      </div>
      {groups.map((group) => (
        <div key={group.name} className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {group.label?.text ?? group.name}
          </h3>
          <div className="space-y-0.5">
            {group.facets.map((facet: TurFacetItem) => (
              <button
                key={facet.label + facet.link}
                onClick={() => onNavigate(facet.link)}
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
}: {
  doc: ResolvedDocument;
  onClick: () => void;
}) {
  const fields = doc.raw.fields;
  const dangerLevel = fields.danger_level ?? 0;
  const elements: string[] = Array.isArray(fields.element)
    ? fields.element
    : fields.element
      ? [fields.element]
      : [];
  const creatureTypes: string[] = Array.isArray(fields.creature_type)
    ? fields.creature_type
    : fields.creature_type
      ? [fields.creature_type]
      : [];

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/50 bg-card text-left transition-all duration-300 hover:border-primary/40 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {/* Glow effect on hover */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-gradient-to-b from-primary/5 via-transparent to-transparent" />

      {/* Image */}
      {doc.image && (
        <div className="relative h-48 overflow-hidden bg-muted/30">
          <img
            src={doc.image}
            alt={doc.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-card via-transparent to-transparent" />

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
              isCurrent && "pointer-events-none"
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
  const { history, save: saveSearch, remove: removeSearch, clear: clearHistory } = useTuringSearchHistory();
  const [showDropdown, setShowDropdown] = useState(false);

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
        <div className="absolute inset-0 bg-gradient-to-b from-primary/8 via-background to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/10 via-transparent to-transparent" />

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
              <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight bg-gradient-to-b from-foreground via-foreground to-muted-foreground bg-clip-text text-transparent pb-2">
                The Bestiary
              </h1>
              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-base sm:text-lg leading-relaxed">
                Explore mythical creatures from world folklore. Dragons, spirits,
                leviathans and forgotten beasts await within these pages.
              </p>
            </>
          )}

          {/* Search bar */}
          <div
            className={cn(
              "relative z-50 mx-auto w-full transition-all duration-500",
              isLanding ? "mt-8 max-w-xl" : "max-w-2xl"
            )}
          >
            <div className="relative group">
              {/* Glow ring */}
              <div className="absolute -inset-0.5 rounded-xl bg-gradient-to-r from-primary/30 via-amber-500/20 to-primary/30 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />

              <div className="relative flex items-center gap-2 rounded-xl border border-border/60 bg-card/80 backdrop-blur-sm px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/40">
                <IconSearch className="size-5 text-muted-foreground shrink-0" />
                <Input
                  value={turing.inputValue}
                  onChange={(e) => {
                    turing.setInputValue(e.target.value);
                    fetchSuggestions(e.target.value);
                  }}
                  onFocus={() => setShowDropdown(true)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      clearSuggestions();
                      setShowDropdown(false);
                      saveSearch(turing.inputValue);
                      turing.submitSearch();
                    }
                  }}
                  onBlur={() => globalThis.setTimeout(() => { clearSuggestions(); setShowDropdown(false); }, 150)}
                  placeholder="Search for a creature... dragon, phoenix, kraken..."
                  className="border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:border-transparent text-base placeholder:text-muted-foreground/60"
                  autoComplete="off"
                />
                <Button
                  size="sm"
                  onClick={() => { saveSearch(turing.inputValue); turing.submitSearch(); setShowDropdown(false); }}
                  className="shrink-0 bg-gradient-to-r from-primary to-purple-600 hover:from-primary/90 hover:to-purple-600/90 text-primary-foreground shadow-md"
                >
                  Search
                </Button>
              </div>

              {/* Autocomplete / History Dropdown */}
              {suggestions.length > 0 ? (
                <div className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden max-h-64 overflow-y-auto">
                  {suggestions.map((term) => (
                    <button
                      key={term}
                      type="button"
                      className="w-full px-4 py-2.5 text-left text-sm hover:bg-accent transition-colors flex items-center gap-2 cursor-pointer"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => { turing.setInputValue(term); clearSuggestions(); }}
                    >
                      <IconSearch className="size-3.5 text-muted-foreground shrink-0" />
                      {term}
                    </button>
                  ))}
                </div>
              ) : showDropdown && history.filter((h) => !turing.inputValue || h.toLowerCase().includes(turing.inputValue.toLowerCase())).length > 0 && (
                <div className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden max-h-64 overflow-y-auto">
                  <div className="flex items-center justify-between px-4 py-2 border-b border-border/50">
                    <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Recent searches</span>
                    <button
                      type="button"
                      className="text-xs text-muted-foreground hover:text-destructive transition-colors cursor-pointer"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => { clearHistory(); setShowDropdown(false); }}
                    >
                      Clear all
                    </button>
                  </div>
                  {history.filter((h) => !turing.inputValue || h.toLowerCase().includes(turing.inputValue.toLowerCase())).slice(0, 8).map((term) => (
                    <div
                      key={term}
                      className="flex items-center hover:bg-accent transition-colors"
                    >
                      <button
                        type="button"
                        className="flex-1 px-4 py-2.5 text-left text-sm flex items-center gap-2 cursor-pointer"
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => { turing.setInputValue(term); setShowDropdown(false); }}
                      >
                        <IconClock className="size-3.5 text-muted-foreground shrink-0" />
                        {term}
                      </button>
                      <button
                        type="button"
                        title="Remove from history"
                        className="px-3 py-2.5 text-muted-foreground hover:text-destructive transition-colors cursor-pointer"
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => removeSearch(term)}
                      >
                        <IconTrash className="size-3.5" />
                      </button>
                    </div>
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
            <p className="text-sm text-muted-foreground">
              Consulting the ancient scrolls...
            </p>
          </div>
        )}

        {/* Error state */}
        {turing.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconSkull className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80">
              The tome could not be read. {turing.error}
            </p>
            <Button variant="outline" size="sm" onClick={turing.showAll}>
              Return to the Bestiary
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
                  <p className="text-sm text-muted-foreground">
                    <span className="font-semibold text-foreground">
                      {totalCount}
                    </span>{" "}
                    creature{totalCount !== 1 ? "s" : ""} found
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
                    onClick={turing.showAll}
                    className="text-xs text-muted-foreground"
                  >
                    Clear search
                  </Button>
                </div>
              )}

              {/* Grid */}
              {hasResults ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {turing.documents.map((doc, i) => (
                    <CreatureCard
                      key={doc.raw.fields.id ?? `doc-${i}`}
                      doc={doc}
                      onClick={() => goToDetail(doc)}
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
                    onClick={turing.showAll}
                  >
                    Show all creatures
                  </Button>
                </div>
              )}

              {/* Pagination */}
              <Pagination items={pagination} onNavigate={turing.navigate} />
            </div>
          </>
        )}

        {/* Landing state - no search yet, show all */}
        {turing.status === "idle" && (
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
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex items-center justify-between text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span>Mythical Creatures Bestiary</span>
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
