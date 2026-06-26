import { useMemo, useState } from "react";
import { Search, Sparkles } from "lucide-react";
import { SEARCH_DOCS } from "@/lib/site-content";
import { cn } from "@/lib/utils";

const TYPES = ["All", ...Array.from(new Set(SEARCH_DOCS.map((d) => d.type)))];

/**
 * In-page interactive search demo. Filters a small sample corpus client-side so
 * the hero shows the product working with zero backend. Wiring it to a public
 * Turing instance for real semantic search is tracked as ROADMAP T477/T478.
 */
export function SearchDemo() {
  const [query, setQuery] = useState("");
  const [type, setType] = useState("All");

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    return SEARCH_DOCS.filter((d) => {
      const matchesType = type === "All" || d.type === type;
      const matchesQuery =
        !q ||
        d.title.toLowerCase().includes(q) ||
        d.snippet.toLowerCase().includes(q) ||
        d.type.toLowerCase().includes(q);
      return matchesType && matchesQuery;
    });
  }, [query, type]);

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
          demo
        </span>
      </div>

      {/* facets */}
      <div className="flex flex-wrap gap-1.5 border-b border-border px-4 py-2.5">
        {TYPES.map((t) => (
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
            <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
              {d.snippet}
            </p>
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
          {results.length} result{results.length === 1 ? "" : "s"} · sample data
        </span>
        <span className="inline-flex items-center gap-1">
          <Sparkles className="size-3.5 text-primary" />
          Powered by Viglet Turing ES
        </span>
      </div>
    </div>
  );
}
