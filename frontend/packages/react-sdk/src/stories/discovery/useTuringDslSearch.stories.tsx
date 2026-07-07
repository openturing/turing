import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { missionDocuments } from "../__mocks__/fixtures";
import type { TurDslSearchRequest, TurDslSearchHit } from "../../core/api";

/**
 * # useTuringDslSearch
 *
 * Runs a **structured, Elasticsearch-compatible query DSL** against a Semantic
 * Navigation site (`POST /sn/{site}/_search`) and returns the raw ES-shaped
 * response — `hits.total`, `hits.hits[]` with `_id` / `_score` / `_source`,
 * plus optional `aggregations`. This is the **typed query** path, deliberately
 * distinct from free-text search:
 *
 * - **Free-text search** (`useTuringSearch`) takes a string `q`, applies the
 *   site's relevance pipeline, and hands back rendered widget data (facets,
 *   pagination, spellcheck). Great for a search box.
 * - **DSL search** (`useTuringDslSearch`) takes a **query expression object** —
 *   `bool` / `must` / `filter` / `range` / `term` — and gives you full control
 *   over matching, filtering, scoring, sorting and aggregations. Great for
 *   dashboards, relevance experiments, or a catalog copilot's tool layer that
 *   needs to build queries programmatically from JS.
 *
 * Think of it as the difference between typing into Google versus writing the
 * `WHERE` clause yourself.
 *
 * ## Key Features
 * - `search(request)`: fire a `TurDslSearchRequest` (`query`, `from`, `size`,
 *   `sort`, `_source`, `aggs`, `min_score`, …) — extra ES keys pass through
 *   unchanged
 * - `data`: the `TurDslSearchResponse` (`took`, `hits.total.value`,
 *   `hits.hits[]`, `aggregations?`)
 * - `status`: `"idle" | "loading" | "success" | "error"` for UI binding
 * - `error`: human-readable message when the query is rejected
 * - `locale`: per-call override; falls back to the provider locale, then `"en"`
 *
 * ## Usage
 * ```tsx
 * const { search, data, status, error } = useTuringDslSearch();
 *
 * // agency = NASA AND outcome = Success AND year > 1969
 * await search({
 *   query: {
 *     bool: {
 *       filter: [
 *         { term: { agency: "NASA" } },
 *         { term: { outcome: "Success" } },
 *         { range: { year: { gt: 1969 } } },
 *       ],
 *     },
 *   },
 *   sort: [{ year: "asc" }],
 *   size: 10,
 * });
 *
 * if (status === "success") {
 *   console.log(`${data?.hits.total.value} missions matched`);
 *   data?.hits.hits.forEach((h) => console.log(h._id, h._source.title));
 * }
 * ```
 *
 * ## When to use
 *
 * - **Dashboards & reports**: aggregate counts, filter by exact field values,
 *   sort deterministically — no relevance fuzziness
 * - **Relevance experiments**: tweak `min_score`, `boost`, scoring and compare
 *   `hits.max_score` runs side-by-side
 * - **Copilot tool layer**: let an LLM emit a JSON query object that you pass
 *   straight to `search()` — the DSL is the contract
 * - **Power filters**: range/date windows, boolean combinations, multi-field
 *   `must`/`should`/`must_not` that a single search box can't express
 *
 * ## About this story
 *
 * Fully self-contained — **no Provider, hook, or network**. Preset DSL queries
 * (and the field builder) filter the five Space Missions fixtures **locally**;
 * a tiny in-memory matcher interprets `term` / `range` / `bool.filter`. The
 * left panel shows the exact `TurDslSearchRequest` JSON you'd send; the right
 * panel shows the ES-shaped result count and hit cards. In production the only
 * change is calling the real `search(request)` from the hook.
 */

/* ── Fixture → flat, ES-style `_source` rows ──────────────────────────── */

interface MissionRow {
  readonly id: string;
  readonly title: string;
  readonly agency: string;
  readonly outcome: string;
  readonly program: string;
  readonly year: number;
  readonly crew_size: number;
  readonly description: string;
}

function toYear(date: unknown): number {
  const s = String(date ?? "");
  const m = /(\d{4})/.exec(s);
  return m ? Number(m[1]) : 0;
}

const MISSION_ROWS: ReadonlyArray<MissionRow> = missionDocuments.map((doc) => {
  const f = doc.fields;
  const agency = Array.isArray(f.agency) ? String(f.agency[0]) : String(f.agency ?? "");
  return {
    id: String(f.id),
    title: String(f.title ?? ""),
    agency,
    outcome: String(f.outcome ?? ""),
    program: String(f.program ?? ""),
    year: toYear(f.date),
    crew_size: Number(f.crew_size ?? 0),
    description: String(f.description ?? ""),
  };
});

/* ── Minimal local DSL interpreter (term / range / bool.filter) ───────── */

type Clause = Record<string, unknown>;

function matchTerm(row: MissionRow, term: Record<string, unknown>): boolean {
  return Object.entries(term).every(([field, value]) => {
    const actual = (row as unknown as Record<string, unknown>)[field];
    return String(actual).toLowerCase() === String(value).toLowerCase();
  });
}

function matchRange(row: MissionRow, range: Record<string, unknown>): boolean {
  return Object.entries(range).every(([field, ops]) => {
    const actual = Number((row as unknown as Record<string, unknown>)[field]);
    const o = ops as Record<string, number>;
    if (o.gt !== undefined && !(actual > o.gt)) return false;
    if (o.gte !== undefined && !(actual >= o.gte)) return false;
    if (o.lt !== undefined && !(actual < o.lt)) return false;
    if (o.lte !== undefined && !(actual <= o.lte)) return false;
    return true;
  });
}

function matchClause(row: MissionRow, clause: Clause): boolean {
  if (clause.term) return matchTerm(row, clause.term as Record<string, unknown>);
  if (clause.range) return matchRange(row, clause.range as Record<string, unknown>);
  if (clause.match_all) return true;
  return true; // unknown clause → permissive in the simulation
}

function runLocalDsl(request: TurDslSearchRequest): MissionRow[] {
  const query = (request.query ?? { match_all: {} }) as Clause;
  const bool = query.bool as Record<string, unknown> | undefined;
  const filters = (bool?.filter as Clause[] | undefined) ?? (query.term || query.range ? [query] : []);

  let rows = MISSION_ROWS.filter((row) =>
    filters.length === 0 ? true : filters.every((c) => matchClause(row, c)),
  );

  // sort: [{ field: "asc" | "desc" }]
  const sort = request.sort as Array<Record<string, "asc" | "desc">> | undefined;
  if (sort && sort.length > 0) {
    const [field, dir] = Object.entries(sort[0])[0];
    rows = [...rows].sort((a, b) => {
      const av = (a as unknown as Record<string, unknown>)[field];
      const bv = (b as unknown as Record<string, unknown>)[field];
      const cmp = av! < bv! ? -1 : av! > bv! ? 1 : 0;
      return dir === "desc" ? -cmp : cmp;
    });
  }

  const from = request.from ?? 0;
  const size = request.size ?? 10;
  return rows.slice(from, from + size);
}

function rowToHit(row: MissionRow, score: number): TurDslSearchHit {
  return {
    _id: row.id,
    _score: score,
    _source: { ...row },
  };
}

/* ── Preset queries ───────────────────────────────────────────────────── */

interface Preset {
  readonly id: string;
  readonly emoji: string;
  readonly label: string;
  readonly hint: string;
  readonly request: TurDslSearchRequest;
}

const PRESETS: ReadonlyArray<Preset> = [
  {
    id: "nasa-success-after-69",
    emoji: "🚀",
    label: "NASA · Success · after 1969",
    hint: "agency = NASA AND outcome = Success AND year > 1969",
    request: {
      query: {
        bool: {
          filter: [
            { term: { agency: "NASA" } },
            { term: { outcome: "Success" } },
            { range: { year: { gt: 1969 } } },
          ],
        },
      },
      sort: [{ year: "asc" }],
      size: 10,
    },
  },
  {
    id: "esa-only",
    emoji: "🛰️",
    label: "ESA missions only",
    hint: "agency = ESA",
    request: {
      query: { bool: { filter: [{ term: { agency: "ESA" } }] } },
      size: 10,
    },
  },
  {
    id: "crewed",
    emoji: "👩‍🚀",
    label: "Crewed missions (crew ≥ 1)",
    hint: "crew_size >= 1",
    request: {
      query: { bool: { filter: [{ range: { crew_size: { gte: 1 } } }] } },
      sort: [{ crew_size: "desc" }],
      size: 10,
    },
  },
  {
    id: "failures",
    emoji: "💥",
    label: "Mission failures",
    hint: "outcome = Failure",
    request: {
      query: { bool: { filter: [{ term: { outcome: "Failure" } }] } },
      size: 10,
    },
  },
  {
    id: "all",
    emoji: "🌌",
    label: "All missions (match_all)",
    hint: "match_all — no filter",
    request: { query: { match_all: {} }, sort: [{ year: "asc" }], size: 10 },
  },
];

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

/* ── Demo component ───────────────────────────────────────────────────── */

function DslSearchDemo({ dark }: { dark: boolean }) {
  const [activeId, setActiveId] = useState<string>(PRESETS[0].id);
  const active = PRESETS.find((p) => p.id === activeId) ?? PRESETS[0];

  const hits = useMemo<TurDslSearchHit[]>(() => {
    const rows = runLocalDsl(active.request);
    // Synthetic descending score so cards show a relevance-like signal.
    return rows.map((row, i) => rowToHit(row, Number((1 - i * 0.07).toFixed(2))));
  }, [active]);

  const bg = dark ? "#0a0a0f" : "#f8fafc";
  const card = dark ? "#16161f" : "#ffffff";
  const border = dark ? "#1e1e2e" : "#e2e8f0";
  const text = dark ? "#e2e8f0" : "#0f172a";
  const muted = dark ? "#94a3b8" : "#64748b";
  const codeBg = dark ? "#0f0f17" : "#0f172a";

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        background: bg,
        padding: "20px",
        borderRadius: "14px",
        color: text,
        width: "860px",
        maxWidth: "100%",
        boxSizing: "border-box",
      }}
    >
      {/* Header */}
      <div style={{ marginBottom: "16px" }}>
        <div
          style={{
            display: "inline-block",
            background: GRADIENT,
            color: "white",
            fontSize: "11px",
            fontWeight: 700,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
            padding: "4px 10px",
            borderRadius: "999px",
          }}
        >
          🧮 Structured DSL Search
        </div>
        <p style={{ margin: "10px 0 0", fontSize: "13px", color: muted }}>
          Pick a structured query — the JSON you'd POST to{" "}
          <code style={{ color: text }}>/sn/&#123;site&#125;/_search</code> is on the
          left; the live ES-shaped result is on the right.
        </p>
      </div>

      {/* Preset buttons */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: "8px", marginBottom: "16px" }}>
        {PRESETS.map((p) => {
          const isActive = p.id === activeId;
          return (
            <button
              key={p.id}
              type="button"
              onClick={() => setActiveId(p.id)}
              style={{
                padding: "8px 14px",
                borderRadius: "999px",
                border: isActive ? "none" : `1px solid ${border}`,
                background: isActive ? GRADIENT : card,
                color: isActive ? "white" : text,
                fontSize: "12.5px",
                fontWeight: 600,
                cursor: "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: "6px",
              }}
            >
              <span>{p.emoji}</span>
              {p.label}
            </button>
          );
        })}
      </div>

      <div style={{ display: "flex", gap: "16px", alignItems: "flex-start" }}>
        {/* DSL request panel */}
        <div style={{ flex: "1 1 0", minWidth: 0 }}>
          <div
            style={{
              fontSize: "11px",
              fontWeight: 700,
              textTransform: "uppercase",
              letterSpacing: "0.05em",
              color: muted,
              marginBottom: "6px",
            }}
          >
            TurDslSearchRequest
          </div>
          <div style={{ fontSize: "12px", color: muted, marginBottom: "8px" }}>
            {active.hint}
          </div>
          <pre
            style={{
              margin: 0,
              padding: "14px",
              background: codeBg,
              color: "#a5d6ff",
              borderRadius: "10px",
              fontSize: "12px",
              lineHeight: 1.5,
              overflowX: "auto",
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
            }}
          >
            {JSON.stringify(active.request, null, 2)}
          </pre>
        </div>

        {/* Result panel */}
        <div style={{ flex: "1 1 0", minWidth: 0 }}>
          <div
            style={{
              fontSize: "11px",
              fontWeight: 700,
              textTransform: "uppercase",
              letterSpacing: "0.05em",
              color: muted,
              marginBottom: "6px",
            }}
          >
            Response · hits.total
          </div>
          <div
            style={{
              display: "flex",
              alignItems: "baseline",
              gap: "8px",
              marginBottom: "10px",
            }}
          >
            <span
              style={{
                fontSize: "28px",
                fontWeight: 800,
                background: GRADIENT,
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
                backgroundClip: "text",
              }}
            >
              {hits.length}
            </span>
            <span style={{ fontSize: "12px", color: muted }}>
              {hits.length === 1 ? "mission matched" : "missions matched"}
            </span>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            {hits.length === 0 && (
              <div
                style={{
                  padding: "16px",
                  border: `1px dashed ${border}`,
                  borderRadius: "10px",
                  fontSize: "13px",
                  color: muted,
                  textAlign: "center",
                }}
              >
                No hits — the filter excluded every document.
              </div>
            )}
            {hits.map((hit) => {
              const src = hit._source as unknown as MissionRow;
              return (
                <div
                  key={hit._id}
                  style={{
                    border: `1px solid ${border}`,
                    borderRadius: "10px",
                    background: card,
                    padding: "10px 12px",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: "8px",
                    }}
                  >
                    <strong style={{ fontSize: "13.5px" }}>{src.title}</strong>
                    <span
                      style={{
                        fontSize: "10.5px",
                        fontWeight: 700,
                        color: "#4f46e5",
                        background: dark ? "#1e1b4b" : "#eef2ff",
                        padding: "2px 7px",
                        borderRadius: "999px",
                      }}
                    >
                      _score {hit._score}
                    </span>
                  </div>
                  <div style={{ marginTop: "5px", display: "flex", gap: "6px", flexWrap: "wrap" }}>
                    <Tag dark={dark}>{src.agency}</Tag>
                    <Tag dark={dark}>{src.outcome}</Tag>
                    <Tag dark={dark}>{src.year}</Tag>
                    {src.crew_size > 0 && <Tag dark={dark}>crew {src.crew_size}</Tag>}
                  </div>
                  <code style={{ fontSize: "10.5px", color: muted }}>_id: {hit._id}</code>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

function Tag({ children, dark }: { children: ReactNode; dark: boolean }) {
  return (
    <span
      style={{
        fontSize: "10.5px",
        fontWeight: 600,
        color: dark ? "#cbd5e1" : "#475569",
        background: dark ? "#1e1e2e" : "#f1f5f9",
        padding: "2px 7px",
        borderRadius: "6px",
      }}
    >
      {children}
    </span>
  );
}

/* ── Storybook meta ───────────────────────────────────────────────────── */

const meta: Meta<typeof DslSearchDemo> = {
  title: "Discovery/useTuringDslSearch",
  component: DslSearchDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Structured Elasticsearch-compatible DSL search against a site (`POST /sn/{site}/_search`) — the typed-query counterpart to free-text search. This story is a fully self-contained simulation: preset query expressions (`bool` / `term` / `range` / `match_all`) filter the Space Missions fixtures locally; the exact `TurDslSearchRequest` JSON sits beside the live ES-shaped result count and hit cards. No Provider, hook, or network involved.",
      },
    },
  },
  argTypes: {
    dark: {
      description: "Render the dark variant (Viglet dark surfaces).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof DslSearchDemo>;

export const Playground: Story = {
  name: "🚀 DSL query playground",
  args: { dark: false },
};

export const DarkMode: Story = {
  name: "🧮 Dark mode (mission control)",
  args: { dark: true },
};
