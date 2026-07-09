import { useEffect, useMemo, useRef, useState } from "react";
import { Search, Sparkles } from "lucide-react";
import { fetchSearch, resolveDocuments, type TuringClient } from "@viglet/turing-sdk";
import { SEARCH_DOCS, type SearchDoc } from "@/lib/site-content";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

// T478/T620 — dogfooding: this very demo runs on the product's own zero-dependency
// `@viglet/turing-sdk`. When a public demo instance is configured it does REAL
// semantic search against it; otherwise it filters the bundled sample corpus. Set
// VITE_TURING_DEMO_URL at build time to go live; the SN site is pinned via
// VITE_TURING_DEMO_SITE or auto-discovered at runtime (see @/lib/turing-demo).

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
    const type = fields.type ?? fields.templateName ?? fields.source ?? "Result";
    return {
      title: d.title || "Untitled",
      type: String(type),
      snippet: (d.description || d.text || "").slice(0, 160),
    };
  });
}

export function SearchDemo() {
  const { client, site, live } = useTuringDemo();
  const [query, setQuery] = useState("");
  const [type, setType] = useState("All");
  const [liveDocs, setLiveDocs] = useState<SearchDoc[] | null>(null);
  const seq = useRef(0);

  // Live mode: debounced search via the SDK; latest-query-wins; null on any
  // failure so the render falls back to the sample corpus (never looks broken).
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
      // In live mode the server already applied the query; only facet client-side.
      const matchesQuery =
        usingLive ||
        !q ||
        d.title.toLowerCase().includes(q) ||
        d.snippet.toLowerCase().includes(q) ||
        d.type.toLowerCase().includes(q);
      return matchesType && matchesQuery;
    });
  }, [sourceDocs, query, type, usingLive]);

  return (
    <div className="overflow-hidden rounded-2xl border border-white/10 bg-card text-card-foreground shadow-2xl shadow-black/50">
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

      {/* results */}
      <ul className="max-h-72 divide-y divide-border overflow-y-auto">
        {results.map((d) => (
          <li key={d.title} className="px-4 py-3 transition-colors hover:bg-muted/50">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold">{d.title}</span>
              <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[0.65rem] font-semibold text-primary">
                {d.type}
              </span>
            </div>
            {d.snippet && (
              <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                {d.snippet}
              </p>
            )}
          </li>
        ))}
        {results.length === 0 && (
          <li className="px-4 py-8 text-center text-sm text-muted-foreground">
            No results — try a different term.
          </li>
        )}
      </ul>

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
