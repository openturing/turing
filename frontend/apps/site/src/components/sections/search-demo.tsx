import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { MessageSquare, Search, Sparkles } from "lucide-react";
import { fetchSearch, resolveDocuments, type TuringClient } from "@viglet/turing-sdk";
import {
  TuringResultList,
  type TuringResolvedDocument,
} from "@viglet/turing-react-ui";
import { SEARCH_DOCS, type SearchDoc } from "@/lib/site-content";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

// Dogfooding: this demo runs on the product's own zero-dependency
// `@viglet/turing-sdk` (live semantic search) and renders results through the
// product's `@viglet/turing-react-ui` `TuringResultList`. When a public demo
// instance is configured it searches the REAL `turing-docs` corpus (the same
// content the chat assistant answers from); otherwise it filters a bundled
// sample corpus. SN highlighting wraps matches in <mark>…</mark>.

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

async function liveSearch(
  client: TuringClient,
  site: string,
  q: string
): Promise<SearchDoc[]> {
  const resp = await fetchSearch(client, site, {
    q: q || "*",
    rows: "8",
    _setlocale: "en_US",
  });
  return resolveDocuments(resp).map((d) => {
    const fields = (d.raw?.fields ?? {}) as Record<string, unknown>;
    // Prefer a human-meaningful grouping for the facet chips: turing-docs groups
    // by `category` (Search / RAG & Chat / …); other sites fall back to `type`.
    const type =
      fields.category ??
      fields.type ??
      fields.templateName ??
      fields.source ??
      "Result";
    return {
      title: d.title || "Untitled",
      type: String(type),
      snippet: (d.description || d.text || "").slice(0, 160),
    };
  });
}

export interface SearchDemoProps {
  /** Drop the standalone card chrome so a parent (the playground) frames it. */
  bare?: boolean;
  /** When set, each result shows an "Ask about this" action (search→ask bridge). */
  onAskAbout?: (title: string) => void;
}

export function SearchDemo({ bare = false, onAskAbout }: Readonly<SearchDemoProps>) {
  const { client, site, live } = useTuringDemo();
  const [query, setQuery] = useState("");
  const [type, setType] = useState("All");
  const [liveDocs, setLiveDocs] = useState<SearchDoc[] | null>(null);
  const seq = useRef(0);

  useEffect(() => {
    if (!client || !site) return;
    const mine = ++seq.current;
    const t = window.setTimeout(() => {
      liveSearch(client, site, query)
        .then((docs) => {
          if (mine === seq.current) setLiveDocs(docs);
        })
        .catch(() => {
          if (mine === seq.current) setLiveDocs(null);
        });
    }, 250);
    return () => window.clearTimeout(t);
  }, [query, client, site]);

  const usingLive = live && liveDocs !== null;
  const sourceDocs = usingLive ? (liveDocs as SearchDoc[]) : SEARCH_DOCS;
  const types = useMemo(
    () => ["All", ...Array.from(new Set(sourceDocs.map((d) => d.type)))],
    [sourceDocs]
  );

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    return sourceDocs.filter((d) => {
      const matchesType = type === "All" || d.type === type;
      const matchesQuery =
        usingLive ||
        !q ||
        d.title.toLowerCase().includes(q) ||
        d.snippet.toLowerCase().includes(q) ||
        d.type.toLowerCase().includes(q);
      return matchesType && matchesQuery;
    });
  }, [sourceDocs, query, type, usingLive]);

  // Adapt the demo docs to the product's TuringResolvedDocument shape so the
  // real TuringResultList renders them (dogfooding the shipping component).
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
      {/* search input */}
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
          {usingLive ? "live" : "demo"}
        </span>
      </div>

      {/* facets */}
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

      {/* results — rendered via the product's TuringResultList */}
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

      {/* footer */}
      <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
        <span>
          {results.length} result{results.length === 1 ? "" : "s"} ·{" "}
          {usingLive ? "live demo" : "sample data"}
        </span>
        <span className="inline-flex items-center gap-1">
          <Sparkles className="size-3.5 text-primary" />
          Built with @viglet/turing-sdk
        </span>
      </div>
    </div>
  );
}

/** Strip <mark> tags for a clean prompt when bridging a result into the chat. */
function stripMarks(text: string): string {
  return text.replace(/<\/?mark>/gi, "");
}
