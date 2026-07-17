import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  ChevronDown,
  MessageSquare,
  Search,
  SlidersHorizontal,
  Sparkles,
  Square,
  SquareCheck,
  X,
} from "lucide-react";
import {
  createAutoComplete,
  createSearchController,
  createSimilarController,
  type AutoCompleteController,
  type ResolvedDocument,
  type SearchController,
  type SimilarController,
  type SimilarStatus,
  type TurFacetGroup,
  type TuringAnalytics,
  type TurSimilarResult,
  type TurSpellCheck,
} from "@viglet/turing-sdk";
import {
  TuringResultList,
  type TuringResolvedDocument,
} from "@viglet/turing-react-ui";
import { SEARCH_DOCS, type SearchDoc } from "@/lib/site-content";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

// Dogfooding: this demo runs on the product's own zero-dependency
// `@viglet/turing-sdk` and renders results through the product's
// `@viglet/turing-react-ui` `TuringResultList`. When a public demo instance is
// configured (T477/T620) it drives the REAL `turing-docs` corpus and shows the
// full production search surface (T484): live **facet groups**, keystroke
// **autocomplete** (`createAutoComplete`) and "did you mean" **spell-check** —
// all via the SDK, search-only (no per-keystroke LLM chat call, `chat:false`).
// Each result also offers **"Related by AI"** (T485) — `createSimilarController`
// surfaces semantically similar documents for a clicked result.
// Otherwise it filters a bundled sample corpus so the widget never looks broken.

const LOCALE = "en_US";
// Keep the facet rail compact: at most this many groups, this many chips each.
const MAX_FACET_GROUPS = 3;
const MAX_FACET_ITEMS = 6;
const DEFAULT_SEARCH_PLACEHOLDER = "Search your content…  (try “SSO”, “RAG”, “API”)";

// Semantic Navigation highlighting wraps matched terms in <mark>…</mark>.
// Render those as real <mark> elements while letting React escape everything
// else — so the highlight shows (not the literal tag) with no raw-HTML/XSS risk.
function Highlighted({ text }: { text: string }) {
  if (!text.includes("<mark>")) return <>{text}</>;
  const nodes: ReactNode[] = [];
  let inMark = false;
  text.split(/(<mark>|<\/mark>)/gi).forEach((part, i) => {
    if (/^<mark>$/i.test(part)) {
      inMark = true;
    } else if (/^<\/mark>$/i.test(part)) {
      inMark = false;
    } else if (part) {
      nodes.push(
        inMark ? (
          <mark key={i} className="rounded bg-primary/20 px-0.5 text-primary">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        )
      );
    }
  });
  return <>{nodes}</>;
}

/** Strip <mark> tags for a clean prompt when bridging a result into the chat. */
function stripMarks(text: string): string {
  return text.replace(/<\/?mark>/gi, "");
}

/**
 * Whether a result has enough body to be worth "Ask about this". A nav/landing
 * hub (e.g. WKND's region pages) is indexed with a title but no snippet/text, so
 * seeding the chat with it dead-ends ("no info about X"). Gate the CTA on having
 * a non-empty description or text — "Related" stays available for such pages.
 */
function isAskable(document: TuringResolvedDocument): boolean {
  const body = `${document.description ?? ""} ${document.text ?? ""}`;
  return stripMarks(body).trim().length > 0;
}

/** The human-meaningful badge for a result (category on turing-docs; else type). */
function docBadge(raw: TuringResolvedDocument["raw"]): string {
  const f = raw.fields as Record<string, unknown>;
  return String(
    f.category ?? f.type ?? f.templateName ?? f.source ?? "Result"
  );
}

/**
 * The result title. In live mode a resolved `url` makes it a link that opens the
 * source page (new tab, like "Related by AI"); offline sample docs have no url,
 * so it stays plain text. The "Ask"/"Related" actions render as sibling buttons
 * below, so there's no click-target overlap to guard against.
 */
function ResultTitle({
  title,
  url,
  onOpen,
}: Readonly<{ title: string; url?: string; onOpen?: () => void }>) {
  const inner = <Highlighted text={title} />;
  if (!url) {
    return <span className="text-sm font-semibold">{inner}</span>;
  }
  return (
    <a
      href={url}
      target="_blank"
      rel="noreferrer"
      onClick={onOpen}
      className="text-sm font-semibold hover:text-primary hover:underline"
    >
      {inner}
    </a>
  );
}

export interface SearchDemoProps {
  /** Drop the standalone card chrome so a parent (the playground) frames it. */
  bare?: boolean;
  /** When set, each result shows an "Ask about this" action (search→ask bridge). */
  onAskAbout?: (title: string, id?: string) => void;
  /** Pin a specific demo SN site (else the env pin / first discovered). */
  siteOverride?: string;
  /** Offline fallback corpus (default = the Turing-docs sample). */
  sampleDocs?: SearchDoc[];
  /** Search input placeholder (per-page flavor). */
  placeholder?: string;
}

export function SearchDemo({
  bare = false,
  onAskAbout,
  siteOverride,
  sampleDocs,
  placeholder,
}: Readonly<SearchDemoProps>) {
  const { client, site, live, analytics } = useTuringDemo(siteOverride);

  if (live && client && site) {
    return (
      <LiveSearchDemo
        key={site}
        client={client}
        site={site}
        analytics={analytics}
        bare={bare}
        onAskAbout={onAskAbout}
        placeholder={placeholder}
      />
    );
  }
  return (
    <SampleSearchDemo
      bare={bare}
      onAskAbout={onAskAbout}
      docs={sampleDocs}
      placeholder={placeholder}
    />
  );
}

// ── Live mode ────────────────────────────────────────────────────────────────
// Drives the real corpus through the SDK's search + autocomplete controllers.

function LiveSearchDemo({
  client,
  site,
  analytics,
  bare,
  onAskAbout,
  placeholder,
}: Readonly<{
  client: NonNullable<ReturnType<typeof useTuringDemo>["client"]>;
  site: string;
  analytics: TuringAnalytics;
  bare: boolean;
  onAskAbout?: (title: string, id?: string) => void;
  placeholder?: string;
}>) {
  const [query, setQuery] = useState("");
  const [documents, setDocuments] = useState<ResolvedDocument[]>([]);
  const [facets, setFacets] = useState<TurFacetGroup[]>([]);
  const [spellCheck, setSpellCheck] = useState<TurSpellCheck | null>(null);
  const [cleanUpFacets, setCleanUpFacets] = useState<string>("");
  const [hasFilters, setHasFilters] = useState(false);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  // On mobile the facet rail is collapsed behind a "Filters" toggle so results
  // are visible immediately; on sm+ it's always shown (see the rail classes).
  const [filtersOpen, setFiltersOpen] = useState(false);

  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [acOpen, setAcOpen] = useState(false);

  // "Related by AI" (T485): similar documents for a clicked result.
  const [similar, setSimilar] = useState<TurSimilarResult[]>([]);
  const [similarStatus, setSimilarStatus] = useState<SimilarStatus>("idle");
  const [similarSeed, setSimilarSeed] = useState<string | null>(null);

  const controllerRef = useRef<SearchController | null>(null);
  const acRef = useRef<AutoCompleteController | null>(null);
  const similarRef = useRef<SimilarController | null>(null);
  const searchTimer = useRef<number | undefined>(undefined);

  // Build the controllers once per (client, site). `chat:false` keeps this a
  // pure search surface — no LLM call on every keystroke (T484).
  useEffect(() => {
    const controller = createSearchController(
      client,
      { site, locale: LOCALE, sort: "relevance" },
      { rows: "8", q: "*" },
      // T462 — emit search funnel events (query, no-results, result clicks) to
      // GA4 via the shared bus; `chat:false` keeps it a pure (no-LLM) surface.
      { chat: false, analytics }
    );
    controllerRef.current = controller;
    const unsub = controller.subscribe((s) => {
      setDocuments(s.documents);
      setFacets(s.data?.widget?.facet ?? []);
      setSpellCheck(s.data?.widget?.spellCheck ?? null);
      setCleanUpFacets(s.data?.widget?.cleanUpFacets ?? "");
      setHasFilters((s.params.fq?.length ?? 0) > 0);
      setTotal(s.data?.queryContext?.count ?? s.documents.length);
      setLoading(s.status === "loading");
    });

    const ac = createAutoComplete(client, { site, locale: LOCALE }, 200);
    acRef.current = ac;
    const unsubAc = ac.subscribe((s) => setSuggestions(s.suggestions));

    const similarController = createSimilarController(client, site, {
      rows: 5,
      locale: LOCALE,
    });
    similarRef.current = similarController;
    const unsubSimilar = similarController.subscribe((s) => {
      setSimilar(s.results);
      setSimilarStatus(s.status);
    });

    void controller.search({ q: "*", rows: "8", _setlocale: LOCALE, sort: "relevance" });

    return () => {
      unsub();
      unsubAc();
      unsubSimilar();
      if (searchTimer.current) window.clearTimeout(searchTimer.current);
    };
  }, [client, site, analytics]);

  function loadSimilar(id: string, title: string) {
    setSimilarSeed(title);
    void similarRef.current?.load(id);
  }

  function clearSimilar() {
    setSimilarSeed(null);
    similarRef.current?.clear();
  }

  // Reset every active facet filter. When the current result set is empty the
  // response carries no facet groups (and often no cleanup link), so the facet
  // rail — and its "Clear filters" button — would vanish, trapping the user with
  // filters they can't remove. Prefer the engine's cleanup link when present;
  // otherwise fall back to a fresh, unfiltered base search.
  function clearFilters() {
    clearSimilar();
    setQuery("");
    if (cleanUpFacets) {
      void controllerRef.current?.navigate(cleanUpFacets);
    } else {
      void controllerRef.current?.search({
        q: "*",
        rows: "8",
        _setlocale: LOCALE,
        sort: "relevance",
      });
    }
  }

  // Debounced text query (facet state is preserved on the controller between
  // queries; a new text term resets to page 1 via searchQuery).
  function runQuery(q: string) {
    if (searchTimer.current) window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => {
      void controllerRef.current?.searchQuery(q || "*");
    }, 250);
  }

  function onInput(value: string) {
    setQuery(value);
    setAcOpen(true);
    clearSimilar();
    acRef.current?.fetch(value);
    runQuery(value);
  }

  function pickSuggestion(s: string) {
    setQuery(s);
    setAcOpen(false);
    clearSimilar();
    acRef.current?.clear();
    if (searchTimer.current) window.clearTimeout(searchTimer.current);
    void controllerRef.current?.searchQuery(s);
  }

  // A facet item's `link` is a Turing href that toggles that facet on the
  // current query context; navigate re-runs the search with it (selected items
  // carry a removal link, so this toggles both ways).
  const documentsView = documents as unknown as TuringResolvedDocument[];
  const activeCount = Math.max(
    facets.reduce((n, g) => n + g.facets.filter((f) => f.selected).length, 0),
    hasFilters ? 1 : 0
  );

  return (
    <div
      className={cn(
        "flex flex-col overflow-hidden bg-card text-card-foreground",
        bare ? "h-full" : "rounded-2xl border border-white/10 shadow-2xl shadow-black/50"
      )}
    >
      {/* search input + autocomplete */}
      <div className="relative border-b border-border">
        <div className="flex items-center gap-2.5 px-4 py-3.5">
          <Search className="size-4 shrink-0 text-muted-foreground" />
          <input
            value={query}
            onChange={(e) => onInput(e.target.value)}
            onFocus={() => setAcOpen(true)}
            onBlur={() => window.setTimeout(() => setAcOpen(false), 120)}
            placeholder={placeholder ?? DEFAULT_SEARCH_PLACEHOLDER}
            className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            aria-label="Search demo"
            autoComplete="off"
          />
          <span className="hidden shrink-0 rounded border border-border px-1.5 py-0.5 font-mono text-[0.65rem] text-muted-foreground sm:inline">
            live
          </span>
        </div>
        {acOpen && suggestions.length > 0 && (
          <ul className="absolute inset-x-0 top-full z-20 max-h-56 overflow-y-auto border-b border-border bg-popover shadow-lg">
            {suggestions.map((s) => (
              <li key={s}>
                <button
                  type="button"
                  // onMouseDown (not click) so it fires before the input's blur.
                  onMouseDown={(e) => {
                    e.preventDefault();
                    pickSuggestion(s);
                  }}
                  className="flex w-full items-center gap-2 px-4 py-1.5 text-left text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
                >
                  <Search className="size-3.5 shrink-0 opacity-60" />
                  <span className="truncate">{s}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* spell-check "did you mean" */}
      {spellCheck?.correctedText && !spellCheck.usingCorrectedText && (
        <div className="border-b border-border bg-amber-500/5 px-4 py-2 text-xs text-muted-foreground">
          Did you mean{" "}
          <button
            type="button"
            onClick={() => {
              const t = stripMarks(spellCheck.corrected.text);
              setQuery(t);
              clearSimilar();
              const link = spellCheck.corrected.link;
              if (link) void controllerRef.current?.navigate(link);
              else void controllerRef.current?.searchQuery(t);
            }}
            className="font-semibold text-primary hover:underline"
          >
            <Highlighted text={spellCheck.corrected.text} />
          </button>
          ?
        </div>
      )}

      {/* live facet groups — keep the rail (and Clear filters) whenever a filter
          is active, even if a zero-result response returned no facet groups. */}
      {(facets.length > 0 || hasFilters) && (
        <div className="border-b border-border">
          {/* Mobile: collapse filters behind a toggle so results show first. */}
          <button
            type="button"
            onClick={() => setFiltersOpen((v) => !v)}
            aria-expanded={filtersOpen}
            className="flex w-full items-center justify-between px-4 py-2.5 sm:hidden"
          >
            <span className="inline-flex items-center gap-1.5 text-[0.7rem] font-semibold tracking-wide text-muted-foreground uppercase">
              <SlidersHorizontal className="size-3.5" />
              Filters{activeCount > 0 ? ` · ${activeCount}` : ""}
            </span>
            <ChevronDown
              className={cn(
                "size-4 text-muted-foreground transition-transform",
                filtersOpen && "rotate-180"
              )}
            />
          </button>
          <div
            className={cn(
              "flex-col gap-1.5 px-4 pb-2.5 sm:flex sm:pt-2.5",
              filtersOpen ? "flex" : "hidden"
            )}
          >
          {facets.slice(0, MAX_FACET_GROUPS).map((group) => (
            <div key={group.name} className="flex flex-wrap items-center gap-1.5">
              <span className="mr-0.5 text-[0.65rem] font-semibold uppercase tracking-wide text-muted-foreground">
                {group.label?.text || group.name}
              </span>
              {group.facets.slice(0, MAX_FACET_ITEMS).map((item) => (
                // Checkbox affordance (not a solid pill): within one field, picking
                // several values UNIONS them (OR), so the count is "docs with this
                // value", not a narrower subset. The check box makes "pick any,
                // results combine" explicit — a plain "(3)" reads like a drill-down.
                <button
                  key={item.label}
                  type="button"
                  role="checkbox"
                  aria-checked={item.selected}
                  onClick={() => {
                    clearSimilar();
                    void controllerRef.current?.navigate(item.link);
                  }}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors",
                    item.selected
                      ? "border-primary bg-primary/10 text-foreground"
                      : "border-border bg-muted text-muted-foreground hover:border-primary/40 hover:text-foreground"
                  )}
                >
                  {item.selected ? (
                    <SquareCheck className="size-3.5 shrink-0 text-primary" />
                  ) : (
                    <Square className="size-3.5 shrink-0 opacity-50" />
                  )}
                  {item.label}
                  <span
                    className={cn(
                      "text-[0.65rem] font-normal tabular-nums",
                      item.selected ? "text-primary/80" : "opacity-60"
                    )}
                  >
                    {item.count}
                  </span>
                </button>
              ))}
            </div>
          ))}
          {hasFilters && (
            <button
              type="button"
              onClick={clearFilters}
              className="mt-0.5 inline-flex w-fit items-center gap-1 text-[0.7rem] font-semibold text-muted-foreground hover:text-foreground"
            >
              <X className="size-3" />
              Clear filters
            </button>
          )}
          </div>
        </div>
      )}

      {/* "Related by AI" — semantic neighbours for a clicked result (T485) */}
      {similarStatus !== "idle" && (
        <div className="border-b border-border bg-primary/5 px-4 py-2.5">
          <div className="mb-1.5 flex items-center justify-between gap-2">
            <span className="inline-flex min-w-0 items-center gap-1.5 text-xs font-semibold text-primary">
              <Sparkles className="size-3.5 shrink-0" />
              Related by AI
              {similarSeed && (
                <span className="truncate font-normal text-muted-foreground">
                  · {similarSeed}
                </span>
              )}
            </span>
            <button
              type="button"
              onClick={clearSimilar}
              className="inline-flex shrink-0 items-center gap-1 text-[0.7rem] font-semibold text-muted-foreground hover:text-foreground"
              aria-label="Close related documents"
            >
              <X className="size-3" />
            </button>
          </div>
          {similarStatus === "loading" && (
            <p className="text-xs text-muted-foreground">Finding related documents…</p>
          )}
          {similarStatus === "error" && (
            <p className="text-xs text-muted-foreground">
              Couldn’t load related documents.
            </p>
          )}
          {similarStatus === "success" && similar.length === 0 && (
            <p className="text-xs text-muted-foreground">No related documents found.</p>
          )}
          {similar.length > 0 && (
            <ul className="flex flex-col gap-1">
              {similar.map((r) => (
                <li key={r.id}>
                  <a
                    href={r.url || undefined}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-2 rounded px-1.5 py-1 text-sm text-foreground hover:bg-muted"
                  >
                    <Sparkles className="size-3 shrink-0 text-primary/60" />
                    <span className="truncate">{r.title}</span>
                    {r.type && (
                      <span className="ml-auto shrink-0 rounded bg-primary/10 px-1.5 py-0.5 text-[0.65rem] font-semibold text-primary">
                        {r.type}
                      </span>
                    )}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* results — rendered via the product's TuringResultList */}
      <TuringResultList
        className={cn(
          "divide-y divide-border overflow-y-auto",
          bare ? "flex-1" : "max-h-72"
        )}
        documents={documentsView}
        emptyComponent={() => (
          <div className="px-4 py-8 text-center text-sm text-muted-foreground">
            {loading ? "Searching…" : "No results — try a different term."}
          </div>
        )}
        itemComponent={({ document }) => (
          <div className="px-4 py-3 transition-colors hover:bg-muted/50">
            <div className="flex items-center gap-2">
              <ResultTitle
                title={document.title}
                url={document.url}
                onOpen={() => {
                  // T462 — record the result click (server CTR + GA4
                  // `turing_search_result_click`) so the search→open funnel is
                  // measurable. No-ops when the doc has no id.
                  const docId = String(
                    (document.raw.fields as Record<string, unknown>).id ?? ""
                  );
                  if (docId) {
                    controllerRef.current?.trackResultClick(
                      docId,
                      documentsView.indexOf(document),
                      query
                    );
                  }
                }}
              />
              <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[0.65rem] font-semibold text-primary">
                {docBadge(document.raw)}
              </span>
            </div>
            {document.description && (
              <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                <Highlighted text={document.description} />
              </p>
            )}
            <div className="mt-1.5 flex flex-wrap items-center gap-3">
              {/* Only offer "Ask about this" when the page has answerable body:
                  a nav/landing hub with no snippet (e.g. WKND's region pages)
                  would dead-end the chat, so we hide the CTA and leave "Related"
                  as the useful action instead. */}
              {onAskAbout && isAskable(document) && (
                <button
                  type="button"
                  onClick={() =>
                    onAskAbout(
                      stripMarks(document.title),
                      String((document.raw.fields as Record<string, unknown>).id ?? "") ||
                        undefined
                    )
                  }
                  className="inline-flex items-center gap-1 text-[0.7rem] font-semibold text-primary hover:underline"
                >
                  <MessageSquare className="size-3" />
                  Ask about this
                </button>
              )}
              {(() => {
                const docId = String(
                  (document.raw.fields as Record<string, unknown>).id ?? ""
                );
                if (!docId) return null;
                return (
                  <button
                    type="button"
                    onClick={() => loadSimilar(docId, stripMarks(document.title))}
                    className="inline-flex items-center gap-1 text-[0.7rem] font-semibold text-primary hover:underline"
                  >
                    <Sparkles className="size-3" />
                    Related
                  </button>
                );
              })()}
            </div>
          </div>
        )}
      />

      {/* footer */}
      <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
        <span>
          {total} result{total === 1 ? "" : "s"} · live demo
        </span>
        <span className="inline-flex items-center gap-1">
          <Sparkles className="size-3.5 text-primary" />
          Built with @viglet/turing-sdk
        </span>
      </div>
    </div>
  );
}

// ── Sample mode ──────────────────────────────────────────────────────────────
// Offline fallback (no demo instance configured): client-side filter over the
// bundled sample corpus, with facet chips derived from the sample `type`.

function SampleSearchDemo({
  bare,
  onAskAbout,
  docs = SEARCH_DOCS,
  placeholder,
}: Readonly<{
  bare: boolean;
  onAskAbout?: (title: string, id?: string) => void;
  docs?: SearchDoc[];
  placeholder?: string;
}>) {
  const [query, setQuery] = useState("");
  const [type, setType] = useState("All");

  const types = useMemo(
    () => ["All", ...Array.from(new Set(docs.map((d) => d.type)))],
    [docs]
  );

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    return docs.filter((d: SearchDoc) => {
      const matchesType = type === "All" || d.type === type;
      const matchesQuery =
        !q ||
        d.title.toLowerCase().includes(q) ||
        d.snippet.toLowerCase().includes(q) ||
        d.type.toLowerCase().includes(q);
      return matchesType && matchesQuery;
    });
  }, [query, type, docs]);

  const documents: TuringResolvedDocument[] = useMemo(
    () =>
      results.map((d) => ({
        url: "",
        title: d.title,
        description: d.snippet,
        date: "",
        image: "",
        text: d.snippet,
        raw: { elevate: false, fields: { type: d.type }, metadata: [] },
      })),
    [results]
  );

  return (
    <div
      className={cn(
        "flex flex-col overflow-hidden bg-card text-card-foreground",
        bare ? "h-full" : "rounded-2xl border border-white/10 shadow-2xl shadow-black/50"
      )}
    >
      <div className="flex items-center gap-2.5 border-b border-border px-4 py-3.5">
        <Search className="size-4 shrink-0 text-muted-foreground" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={placeholder ?? DEFAULT_SEARCH_PLACEHOLDER}
          className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          aria-label="Search demo"
        />
        <span className="hidden shrink-0 rounded border border-border px-1.5 py-0.5 font-mono text-[0.65rem] text-muted-foreground sm:inline">
          demo
        </span>
      </div>

      <div className="flex flex-wrap gap-1.5 border-b border-border px-4 py-2.5">
        {types.map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setType(t)}
            className={cn(
              "rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors",
              t === type
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:text-foreground"
            )}
          >
            {t}
          </button>
        ))}
      </div>

      <TuringResultList
        className={cn(
          "divide-y divide-border overflow-y-auto",
          bare ? "flex-1" : "max-h-72"
        )}
        documents={documents}
        emptyComponent={() => (
          <div className="px-4 py-8 text-center text-sm text-muted-foreground">
            No results — try a different term.
          </div>
        )}
        itemComponent={({ document }) => (
          <div className="px-4 py-3 transition-colors hover:bg-muted/50">
            <div className="flex items-center gap-2">
              <ResultTitle title={document.title} url={document.url} />
              <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[0.65rem] font-semibold text-primary">
                {String(document.raw.fields.type ?? "Result")}
              </span>
            </div>
            {document.description && (
              <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                <Highlighted text={document.description} />
              </p>
            )}
            {onAskAbout && isAskable(document) && (
              <button
                type="button"
                onClick={() => onAskAbout(stripMarks(document.title))}
                className="mt-1.5 inline-flex items-center gap-1 text-[0.7rem] font-semibold text-primary hover:underline"
              >
                <MessageSquare className="size-3" />
                Ask about this
              </button>
            )}
          </div>
        )}
      />

      <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
        <span>
          {results.length} result{results.length === 1 ? "" : "s"} · sample data
        </span>
        <span className="inline-flex items-center gap-1">
          <Sparkles className="size-3.5 text-primary" />
          Built with @viglet/turing-sdk
        </span>
      </div>
    </div>
  );
}
