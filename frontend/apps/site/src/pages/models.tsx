import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { Container } from "@/components/brand";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import {
  catalog,
  formatTokens,
  KIND_BADGE,
  KIND_LABEL,
  MODEL_KINDS,
  type ModelEntry,
  type ModelKind,
  vendorLabel,
} from "@/lib/model-catalog";

const CATALOG_URL = "https://turing.viglet.org/models/catalog.json";
const CATALOG_V1_URL = "https://turing.viglet.org/models/catalog-v1.json";
const SCHEMA_URL = "https://turing.viglet.org/models/catalog.schema.json";

type KindFilter = ModelKind | "ALL";

/** A small colour-coded kind pill (mirrors the Turing admin model picker). */
function KindBadge({ kind }: { kind: ModelKind }) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-[11px] font-medium",
        KIND_BADGE[kind],
      )}
    >
      {KIND_LABEL[kind]}
    </span>
  );
}

/** One model row: label + id + kind + context / dimensions / capabilities. */
function ModelRow({ model }: { model: ModelEntry }) {
  return (
    <div className="flex flex-col gap-1 border-t border-border/60 py-3 first:border-t-0 sm:flex-row sm:items-center sm:gap-3">
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-sm font-medium">{model.label}</span>
        {model.label !== model.id && (
          <code className="truncate text-xs text-muted-foreground">{model.id}</code>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-1.5">
        <KindBadge kind={model.kind} />
        {model.contextWindow != null && (
          <span className="rounded-md bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
            {formatTokens(model.contextWindow)} ctx
          </span>
        )}
        {model.embeddingDimensions != null && (
          <span className="rounded-md bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
            {model.embeddingDimensions.toLocaleString("en-US")} dim
          </span>
        )}
        {model.capabilities?.map((c) => (
          <span
            key={c}
            className="rounded-md border border-border/60 px-1.5 py-0.5 text-[11px] text-muted-foreground"
          >
            {c}
          </span>
        ))}
        {model.deprecated && (
          <span className="rounded-md bg-destructive/10 px-1.5 py-0.5 text-[11px] text-destructive">
            deprecated
          </span>
        )}
      </div>
    </div>
  );
}

/** Dataset structured data so assistants/crawlers can cite the catalog. */
function CatalogJsonLd() {
  const json = {
    "@context": "https://schema.org",
    "@type": "Dataset",
    name: "Viglet Turing ES — Model Catalog",
    description:
      "Vendor-neutral, kind-aware catalog of LLM, embedding, rerank and media models with context windows, embedding dimensions and capabilities.",
    url: "https://turing.viglet.org/models",
    license: "https://www.apache.org/licenses/LICENSE-2.0",
    creator: { "@type": "Organization", name: "Viglet" },
    distribution: {
      "@type": "DataDownload",
      encodingFormat: "application/json",
      contentUrl: CATALOG_URL,
    },
  };
  return (
    <script
      type="application/ld+json"
      // eslint-disable-next-line react/no-danger
      dangerouslySetInnerHTML={{ __html: JSON.stringify(json) }}
    />
  );
}

export function ModelsPage() {
  const [query, setQuery] = useState("");
  const [kind, setKind] = useState<KindFilter>("ALL");

  const totalModels = useMemo(
    () => Object.values(catalog.vendors).reduce((n, list) => n + list.length, 0),
    [],
  );

  // Kinds actually present, in canonical order, for the type filter.
  const presentKinds = useMemo(() => {
    const set = new Set<ModelKind>();
    for (const list of Object.values(catalog.vendors)) for (const m of list) set.add(m.kind);
    return MODEL_KINDS.filter((k) => set.has(k));
  }, []);

  // Vendor → filtered entries (search across id/label/vendor + kind filter).
  const groups = useMemo(() => {
    const q = query.trim().toLowerCase();
    return Object.entries(catalog.vendors)
      .map(([vendor, list]) => {
        const filtered = list.filter((m) => {
          if (kind !== "ALL" && m.kind !== kind) return false;
          if (!q) return true;
          return (
            m.id.toLowerCase().includes(q) ||
            m.label.toLowerCase().includes(q) ||
            vendorLabel(vendor).toLowerCase().includes(q)
          );
        });
        return { vendor, models: filtered };
      })
      .filter((g) => g.models.length > 0);
  }, [query, kind]);

  const shownCount = groups.reduce((n, g) => n + g.models.length, 0);

  const segment = (active: boolean, label: string, onClick: () => void) => (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "rounded-full px-3 py-1 text-sm font-medium transition-colors",
        active
          ? "bg-primary text-primary-foreground"
          : "bg-muted text-muted-foreground hover:text-foreground",
      )}
    >
      {label}
    </button>
  );

  return (
    <div className="min-h-screen bg-background">
      <CatalogJsonLd />
      <SiteNav />

      <header className="border-b border-border/60 bg-gradient-to-b from-muted/40 to-background">
        <Container className="py-14">
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Model Catalog</h1>
          <p className="mt-3 max-w-2xl text-muted-foreground">
            An open, vendor-neutral reference of the models Viglet Turing ES knows about —
            each tagged with its <strong>kind</strong> (chat, embedding, rerank, …) plus
            context window, embedding dimensions and capabilities. Free to consume as a
            public API.
          </p>
          <p className="mt-2 text-sm text-muted-foreground">
            {totalModels} models · {Object.keys(catalog.vendors).length} vendors · schema v
            {catalog.version} · updated {catalog.lastUpdated}
          </p>

          <Card className="mt-6 p-4">
            <p className="text-sm font-semibold">Public API</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Fetch the catalog as JSON (CORS-open). Pin <code>catalog-v1.json</code> for a
              stable schema; <code>catalog.json</code> tracks the latest.
            </p>
            <ul className="mt-3 space-y-1 text-sm">
              <li>
                <a className="text-primary hover:underline" href={CATALOG_URL}>
                  {CATALOG_URL}
                </a>
              </li>
              <li>
                <a className="text-primary hover:underline" href={CATALOG_V1_URL}>
                  {CATALOG_V1_URL}
                </a>{" "}
                <span className="text-muted-foreground">(pinned to v{catalog.version})</span>
              </li>
              <li>
                <a className="text-primary hover:underline" href={SCHEMA_URL}>
                  {SCHEMA_URL}
                </a>{" "}
                <span className="text-muted-foreground">(JSON Schema)</span>
              </li>
            </ul>
            <pre className="mt-3 overflow-x-auto rounded-md bg-muted p-3 text-xs">
              <code>{`curl -s ${CATALOG_V1_URL} \\
  | jq '.vendors | to_entries[].value[] | select(.kind=="EMBEDDING") | .id'`}</code>
            </pre>
          </Card>
        </Container>
      </header>

      <Container className="py-10">
        {/* Controls */}
        <div className="flex flex-col gap-4">
          <label className="relative block max-w-md">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search models, ids, vendors…"
              aria-label="Search models"
              className="h-10 w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </label>
          <div role="tablist" aria-label="Filter by model kind" className="flex flex-wrap gap-1.5">
            {segment(kind === "ALL", "All", () => setKind("ALL"))}
            {presentKinds.map((k) =>
              segment(kind === k, KIND_LABEL[k], () => setKind(k)),
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Showing {shownCount} of {totalModels} models
          </p>
        </div>

        {/* Vendor groups */}
        <div className="mt-8 grid gap-6">
          {groups.map((g) => (
            <Card key={g.vendor} className="p-5">
              <div className="flex items-baseline justify-between gap-3">
                <h2 className="text-lg font-semibold">{vendorLabel(g.vendor)}</h2>
                <span className="text-xs text-muted-foreground">
                  {g.models.length} model{g.models.length === 1 ? "" : "s"}
                </span>
              </div>
              <div className="mt-2">
                {g.models.map((m) => (
                  <ModelRow key={`${g.vendor}:${m.id}`} model={m} />
                ))}
              </div>
            </Card>
          ))}
          {groups.length === 0 && (
            <p className="py-12 text-center text-muted-foreground">
              No models match your search.
            </p>
          )}
        </div>
      </Container>

      <SiteFooter />
    </div>
  );
}
