import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { MessageSquare, Search, Sparkles, X } from "lucide-react";
import {
  createAutoComplete,
  createSearchController,
  type AutoCompleteController,
  type ResolvedDocument,
  type SearchController,
  type TurFacetGroup,
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
// Otherwise it filters a bundled sample corpus so the widget never looks broken.

const LOCALE = "en_US";
// Keep the facet rail compact: at most this many groups, this many chips each.
const MAX_FACET_GROUPS = 3;
const MAX_FACET_ITEMS = 6;

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

/** The human-meaningful badge for a result (category on turing-docs; else type). */
function docBadge(raw: TuringResolvedDocument["raw"]): string {
  const f = raw.fields as Record<string, unknown>;
  return String(
    f.category ?? f.type ?? f.templateName ?? f.source ?? "Result"
  );
}

export interface SearchDemoProps {
  /** Drop the standalone card chrome so a parent (the playground) frames it. */
  bare?: boolean;
  /** When set, each result shows an "Ask about this" action (search→ask bridge). */
  onAskAbout?: (title: string) => void;
}

export function SearchDemo({ bare = false, onAskAbout }: Readonly<SearchDemoProps>) {
  const { client, site, live } = useTuringDemo();

  if (live && client && site) {
    return (
      <LiveSearchDemo
        key={`${site}`}
        client={client}
        site={site}
        bare={bare}
        onAskAbout={onAskAbout}
      />
    );
  }
  return <SampleSearchDemo bare={bare} onAskAbout={onAskAbout} />;
}

// ── Live mode ────────────────────────────────────────────────────────────────
// Drives the real corpus through the SDK's search + autocomplete controllers.

function LiveSearchDemo({
  client,
  site,
  bare,
  onAskAbout,
}: Readonly<{
  client: NonNullable<ReturnType<typeof useTuringDemo>["client"]>;
  site: string;
  bare: boolean;
  onAskAbout?: (title: string) => void;
}>) {
  const [query, setQuery] = useState("");
  const [documents, setDocuments] = useState<ResolvedDocument[]>([]);
  const [facets, setFacets] = useState<TurFacetGroup[]>([]);
  const [spellCheck, setSpellCheck] = useState<TurSpellCheck | null>(null);
  const [cleanUpFacets, setCleanUpFacets] = useState<string>("");
  const [hasFilters, setHasFilters] = useState(false);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [acOpen, setAcOpen] = useState(false);

  const controllerRef = useRef<SearchController | null>(null);
  const acRef = useRef<AutoCompleteController | null>(null);
  const searchTimer = useRef<number | undefined>(undefined);

  // Build the controllers once per (client, site). `chat:false` keeps this a
  // pure search surface — no LLM call on every keystroke (T484).
  useEffect(() => {
    const controller = createSearchController(
      client,
      { site, locale: LOCALE, sort: "relevance" },
      { rows: "8", q: "*" },
      { chat: false }
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

    void controller.search({ q: "*", rows: "8", _setlocale: LOCALE, sort: "relevance" });

    return () => {
      unsub();
      unsubAc();
      if (searchTimer.current) window.clearTimeout(searchTimer.current);
    };
  }, [client, site]);

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
    acRef.current?.fetch(value);
    runQuery(value);
  }

  function pickSuggestion(s: string) {
    setQuery(s);
    setAcOpen(false);
    acRef.current?.clear();
    if (searchTimer.current) window.clearTimeout(searchTimer.current);
    void controllerRef.current?.searchQuery(s);
  }

  // A facet item's `link` is a Turing href that toggles that facet on the
  // current query context; navigate re-runs the search with it (selected items
  // carry a removal link, so this toggles both ways).
  const documentsView = documents as unknown as TuringResolvedDocument[];

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
            placeholder="Search your content…  (try “SSO”, “RAG”, “API”)"
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

      {/* live facet groups */}
      {facets.length > 0 && (
        <div className="flex flex-col gap-1.5 border-b border-border px-4 py-2.5">
          {facets.slice(0, MAX_FACET_GROUPS).map((group) => (
            <div key={group.name} className="flex flex-wrap items-center gap-1.5">
              <span className="mr-0.5 text-[0.65rem] font-semibold uppercase tracking-wide text-muted-foreground">
                {group.label?.text || group.name}
              </span>
              {group.facets.slice(0, MAX_FACET_ITEMS).map((item) => (
                <button
                  key={item.label}
                  type="button"
                  onClick={() => void controllerRef.current?.navigate(item.link)}
                  aria-pressed={item.selected}
                  className={cn(
                    "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors",
                    item.selected
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:text-foreground"
                  )}
                >
                  {item.label}
                  <span
                    className={cn(
                      "text-[0.65rem] font-normal tabular-nums",
                      item.selected ? "text-primary-foreground/70" : "opacity-60"
                    )}
                  >
                    {item.count}
                  </span>
                </button>
              ))}
            </div>
          ))}
          {hasFilters && cleanUpFacets && (
            <button
              type="button"
              onClick={() => void controllerRef.current?.navigate(cleanUpFacets)}
              className="mt-0.5 inline-flex w-fit items-center gap-1 text-[0.7rem] font-semibold text-muted-foreground hover:text-foreground"
            >
              <X className="size-3" />
              Clear filters
            </button>
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
              <span className="text-sm font-semibold">
                <Highlighted text={document.title} />
              </span>
              <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[0.65rem] font-semibold text-primary">
                {docBadge(document.raw)}
              </span>
            </div>
            {document.description && (
              <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                <Highlighted text={document.description} />
              </p>
            )}
            {onAskAbout && (
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
}: Readonly<{ bare: boolean; onAskAbout?: (title: string) => void }>) {
  const [query, setQuery] = useState("");
  const [type, setType] = useState("All");

  const types = useMemo(
    () => ["All", ...Array.from(new Set(SEARCH_DOCS.map((d) => d.type)))],
    []
  );

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    return SEARCH_DOCS.filter((d: SearchDoc) => {
      const matchesType = type === "All" || d.type === type;
      const matchesQuery =
        !q ||
        d.title.toLowerCase().includes(q) ||
        d.snippet.toLowerCase().includes(q) ||
        d.type.toLowerCase().includes(q);
      return matchesType && matchesQuery;
    });
  }, [query, type]);

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
          placeholder="Search your content…  (try “SSO”, “RAG”, “API”)"
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
              <span className="text-sm font-semibold">
                <Highlighted text={document.title} />
              </span>
              <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[0.65rem] font-semibold text-primary">
                {String(document.raw.fields.type ?? "Result")}
              </span>
            </div>
            {document.description && (
              <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                <Highlighted text={document.description} />
              </p>
            )}
            {onAskAbout && (
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
