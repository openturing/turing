import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";
import { vinylDocuments } from "../__mocks__/fixtures";
import type { TurDocument } from "../../core/types";

/**
 * # useTuringSimilar
 *
 * "More like this" / related-items discovery in a single hook. Given a **seed
 * document id**, it loads the items the search engine considers semantically
 * closest — the data behind a "You might also like" rail next to a result or
 * detail page (T384's `GET /sn/{site}/search/similar`).
 *
 * Two strategies, selectable via `mode` (omit for server auto-selection):
 *
 * - **`VECTOR`** — k-NN over the per-document embedding vectors (the hybrid
 *   index built by T383). Captures meaning, so "Kind of Blue" surfaces other
 *   modal-jazz records even with no shared keyword.
 * - **`MLT`** — classic Lucene *More-Like-This*: builds a query from the seed's
 *   most significant terms. Cheaper, lexical, and a sane fallback when a site
 *   has no vectors indexed.
 *
 * When `documentId` is supplied the hook auto-loads on mount and re-loads
 * whenever the id changes; otherwise drive it imperatively with `load(id)`.
 * Concurrent loads are guarded by a monotonic request id — a superseded
 * response is dropped, so rapidly clicking between seeds never flickers stale
 * neighbours.
 *
 * ## Key Features
 * - `documentId` auto-load + `load(id)` imperative trigger
 * - `mode`: force `VECTOR` (semantic) or `MLT` (lexical), or let the server pick
 * - `rows`: cap the rail length (server max 50, default 10)
 * - `results` / `loading` / `error`: minimal render state
 * - `reset()`: clear the rail (e.g. when the detail panel closes)
 * - Race-safe: out-of-order responses are discarded
 *
 * ## Usage
 * ```tsx
 * const { results, loading, error } = useTuringSimilar({
 *   documentId: album.id,
 *   mode: "VECTOR",
 *   rows: 6,
 * });
 *
 * if (loading) return <Spinner />;
 * if (error) return <p>{error}</p>;
 * return (
 *   <aside aria-label="You might also like">
 *     {results.map((r) => (
 *       <a key={r.id} href={r.url}>
 *         {r.title} <small>{r.type}</small>
 *       </a>
 *     ))}
 *   </aside>
 * );
 * ```
 *
 * ## When to use
 * - **Related rail** on a product / article / album detail page to lift
 *   engagement and time-on-site
 * - **"Customers also viewed"** carousels driven by content similarity rather
 *   than collaborative-filtering history (works on day one, no clickstream)
 * - **Editorial discovery**: surface adjacent records when search returns a
 *   single strong hit
 *
 * ## About this story
 *
 * Fully self-contained — no Provider, hook, or network. It reuses the
 * `vinylDocuments` fixture as a fake catalog and computes a **toy similarity
 * score** locally (shared genre + release-era proximity) so the ranking is
 * deterministic and visible. Click an album on the left to set the seed; the
 * "You might also like" rail on the right re-ranks with an animated score bar.
 * Toggle `mode` to compare the semantic (`VECTOR`) and lexical (`MLT`) shapes —
 * the simulation widens the genre overlap weight for `VECTOR` to mimic how
 * embeddings catch cross-genre affinity.
 */

/* ── Local shapes mirroring the real hook's return (kept local for TS hygiene) ── */

type SimulatedMode = "VECTOR" | "MLT" | "AUTO";

interface RankedNeighbour {
  readonly id: string;
  readonly title: string;
  readonly type: string;
  readonly url: string;
  readonly artist: string;
  readonly genres: readonly string[];
  readonly year: number;
  readonly coverArt: string;
  /** Toy similarity in [0,1] — stands in for the engine's relevance score. */
  readonly score: number;
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

function genresOf(doc: TurDocument): string[] {
  const g = doc.fields.genre;
  if (Array.isArray(g)) return g.map(String);
  if (typeof g === "string") return [g];
  return [];
}

function yearOf(doc: TurDocument): number {
  const y = doc.fields.year;
  return typeof y === "number" ? y : Number(y) || 0;
}

/**
 * Toy stand-in for the engine's similarity. Blends genre overlap (Jaccard) with
 * release-era proximity. VECTOR weights the semantic (genre) signal more
 * heavily, mimicking how embeddings catch cross-genre affinity; MLT leans on the
 * lexical/era overlap.
 */
function similarity(seed: TurDocument, other: TurDocument, mode: SimulatedMode): number {
  const a = new Set(genresOf(seed));
  const b = genresOf(other);
  const shared = b.filter((g) => a.has(g)).length;
  const union = new Set([...a, ...b]).size || 1;
  const genreScore = shared / union;

  const eraGap = Math.abs(yearOf(seed) - yearOf(other));
  const eraScore = Math.max(0, 1 - eraGap / 40); // within 40 years -> some affinity

  const genreWeight = mode === "VECTOR" ? 0.8 : mode === "MLT" ? 0.45 : 0.62;
  return Math.min(1, genreWeight * genreScore + (1 - genreWeight) * eraScore);
}

function toNeighbour(doc: TurDocument, score: number): RankedNeighbour {
  const f = doc.fields;
  return {
    id: String(f.id),
    title: String(f.title ?? ""),
    type: "Record",
    url: String(f.url ?? "#"),
    artist: String(f.artist ?? "Unknown"),
    genres: genresOf(doc),
    year: yearOf(doc),
    coverArt: String(f.cover_art ?? f.image ?? ""),
    score,
  };
}

/* ── Interactive demo (simulates useTuringSimilar) ── */

function SimilarRail({
  mode,
  rows,
  theme,
}: {
  mode: SimulatedMode;
  rows: number;
  theme: "light" | "dark";
}) {
  const isDark = theme === "dark";
  const catalog = vinylDocuments;
  const [seedId, setSeedId] = useState<string>(String(catalog[0].fields.id));

  const seed = useMemo(
    () => catalog.find((d) => String(d.fields.id) === seedId) ?? catalog[0],
    [catalog, seedId],
  );

  // Simulates the hook: rank every other doc by similarity, drop the seed,
  // sort desc, cap at `rows`. Mirrors what `results` would hold after load().
  const results = useMemo<RankedNeighbour[]>(() => {
    return catalog
      .filter((d) => String(d.fields.id) !== seedId)
      .map((d) => toNeighbour(d, similarity(seed, d, mode)))
      .sort((x, y) => y.score - x.score)
      .slice(0, rows);
  }, [catalog, seed, seedId, mode, rows]);

  const fg = isDark ? "#e2e8f0" : "#1e293b";
  const sub = isDark ? "#94a3b8" : "#64748b";
  const cardBg = isDark ? "#1e293b" : "#f8fafc";
  const border = isDark ? "#334155" : "#e2e8f0";

  return (
    <div
      style={{
        display: "flex",
        gap: "28px",
        fontFamily: "system-ui, sans-serif",
        color: fg,
        background: isDark ? "#0f172a" : "#ffffff",
        padding: "24px",
        borderRadius: "12px",
        alignItems: "flex-start",
      }}
    >
      {/* ── Seed picker ── */}
      <aside style={{ width: "240px", flexShrink: 0 }}>
        <div
          style={{
            fontSize: "11px",
            fontWeight: 700,
            textTransform: "uppercase",
            letterSpacing: "0.1em",
            color: sub,
            marginBottom: "10px",
          }}
        >
          Pick an album (seed)
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
          {catalog.map((doc) => {
            const id = String(doc.fields.id);
            const selected = id === seedId;
            return (
              <button
                key={id}
                type="button"
                onClick={() => setSeedId(id)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "10px",
                  padding: "8px 10px",
                  borderRadius: "8px",
                  border: selected ? "none" : `1px solid ${border}`,
                  background: selected ? GRADIENT : "transparent",
                  color: selected ? "white" : fg,
                  cursor: "pointer",
                  textAlign: "left",
                }}
              >
                <span style={{ fontSize: "18px", lineHeight: 1 }}>💿</span>
                <span style={{ overflow: "hidden" }}>
                  <span style={{ display: "block", fontSize: "13px", fontWeight: 600 }}>
                    {String(doc.fields.title)}
                  </span>
                  <span
                    style={{
                      display: "block",
                      fontSize: "11px",
                      color: selected ? "rgba(255,255,255,0.85)" : sub,
                    }}
                  >
                    {String(doc.fields.artist)} · {yearOf(doc)}
                  </span>
                </span>
              </button>
            );
          })}
        </div>
      </aside>

      {/* ── Related rail ── */}
      <section style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "10px",
            marginBottom: "4px",
          }}
        >
          <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700 }}>
            🔗 You might also like
          </h3>
          <code
            style={{
              fontSize: "10px",
              fontWeight: 700,
              padding: "2px 8px",
              borderRadius: "999px",
              background: GRADIENT,
              color: "white",
              letterSpacing: "0.04em",
            }}
          >
            mode: {mode}
          </code>
        </div>
        <p style={{ margin: "0 0 16px", fontSize: "12px", color: sub }}>
          Related to <strong style={{ color: fg }}>{String(seed.fields.title)}</strong> ·{" "}
          {results.length} of {rows} requested
        </p>

        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {results.map((r, i) => {
            const pct = Math.round(r.score * 100);
            return (
              <div
                key={r.id}
                style={{
                  display: "flex",
                  gap: "12px",
                  alignItems: "center",
                  padding: "10px 12px",
                  borderRadius: "10px",
                  border: `1px solid ${border}`,
                  background: cardBg,
                }}
              >
                <span
                  style={{
                    flexShrink: 0,
                    width: "22px",
                    fontSize: "13px",
                    fontWeight: 700,
                    color: sub,
                    textAlign: "right",
                  }}
                >
                  {i + 1}
                </span>
                <img
                  src={r.coverArt}
                  alt=""
                  style={{
                    width: "44px",
                    height: "44px",
                    borderRadius: "6px",
                    objectFit: "cover",
                    flexShrink: 0,
                  }}
                />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "8px" }}>
                    <span
                      style={{
                        fontSize: "14px",
                        fontWeight: 600,
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                      }}
                    >
                      {r.title}
                    </span>
                    <span style={{ fontSize: "12px", fontWeight: 700, color: "#4f46e5" }}>
                      {pct}%
                    </span>
                  </div>
                  <div style={{ fontSize: "11px", color: sub, marginBottom: "6px" }}>
                    {r.artist} · {r.genres.join(", ")} · {r.year}
                  </div>
                  {/* Similarity score bar */}
                  <div
                    style={{
                      height: "6px",
                      borderRadius: "999px",
                      background: isDark ? "#0f172a" : "#e2e8f0",
                      overflow: "hidden",
                    }}
                  >
                    <div
                      style={{
                        height: "100%",
                        width: `${pct}%`,
                        borderRadius: "999px",
                        background: GRADIENT,
                        transition: "width 350ms ease",
                      }}
                    />
                  </div>
                </div>
              </div>
            );
          })}
          {results.length === 0 && (
            <div style={{ textAlign: "center", padding: "32px", color: sub }}>
              No related records found.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

const meta: Meta<typeof SimilarRail> = {
  title: "Discovery/useTuringSimilar",
  component: SimilarRail,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Interactive demo simulating `useTuringSimilar` (\"more like this\" discovery). Pick a seed album on the left; the \"You might also like\" rail re-ranks related records with an animated similarity score bar. The score is a local toy blend of genre overlap + release-era proximity (no network) — switch `mode` to compare the semantic (`VECTOR`) vs lexical (`MLT`) ranking shapes.",
      },
    },
  },
  argTypes: {
    mode: {
      description:
        "Similarity strategy. VECTOR = semantic k-NN over embeddings; MLT = lexical More-Like-This; AUTO = let the server pick.",
      control: { type: "inline-radio" },
      options: ["VECTOR", "MLT", "AUTO"],
    },
    rows: {
      description: "Max related items in the rail (server caps at 50; default 10).",
      control: { type: "range", min: 1, max: 4, step: 1 },
    },
    theme: {
      description: "Light or dark surface.",
      control: { type: "inline-radio" },
      options: ["light", "dark"],
    },
  },
};

export default meta;
type Story = StoryObj<typeof SimilarRail>;

export const VectorSemantic: Story = {
  name: "🎵 VECTOR — semantic neighbours",
  args: { mode: "VECTOR", rows: 4, theme: "light" },
};

export const MltLexical: Story = {
  name: "🔗 MLT — lexical More-Like-This",
  args: { mode: "MLT", rows: 4, theme: "light" },
};

export const AutoMode: Story = {
  name: "🎵 AUTO — server-selected strategy",
  args: { mode: "AUTO", rows: 3, theme: "light" },
};

export const VectorDark: Story = {
  name: "🔗 VECTOR rail (dark)",
  args: { mode: "VECTOR", rows: 4, theme: "dark" },
};
