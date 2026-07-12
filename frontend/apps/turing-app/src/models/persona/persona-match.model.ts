/**
 * Persona Match — API models + presentation helpers (Block AT / §XLIII).
 *
 * Types mirror the backend DTOs (`TurPersonaMatchProjectDto`,
 * `TurPersonaMatchSourceDto`, `TurPersonaMatchPersonaRefDto`,
 * `TurPersonaMatchCellDto`, `TurPersonaMatchMatrixDto`) so the query hooks and
 * studio render straight off the wire. The pure helpers (fit colour scale,
 * labels, the two report lenses) carry over from the T701–T702 UI spike.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export type MatchSourceType = "URL" | "SN_DOC" | "ASSET";
export type MatchSourceStatus = "EXTRACTED" | "PENDING" | "FAILED";
export type MatchSchedule = "MANUAL" | "DAILY" | "WEEKLY";
export type MatchPersonaKind = "SPEAKER" | "AUDIENCE" | "BOTH";

export interface MatchSource {
  id: string;
  type: MatchSourceType;
  /** `sourceName` on the wire. */
  name: string;
  ref: string;
  siteName?: string | null;
  charCount: number;
  status: MatchSourceStatus;
  error?: string | null;
}

export interface MatchPersonaRef {
  id: string;
  name: string;
  kind: MatchPersonaKind;
}

export interface MatchMisfit {
  span: string;
  reason: string;
  suggestion: string;
}

/** One cell of the N×N matrix — a single (content × persona) fit result. */
export interface MatchCell {
  sourceId: string;
  personaId: string;
  fitScore: number;
  readability: number;
  llmUsed: boolean;
  summary?: string | null;
  fits: string[];
  misfits: MatchMisfit[];
}

/** Project summary (list) — `sources`/`personas` omitted, counts present. */
export interface MatchProjectSummary {
  id: string;
  name: string;
  description: string | null;
  enabled: boolean;
  schedule: MatchSchedule;
  llmInstanceId: string | null;
  llmName: string | null;
  lastRunAt: string | null;
  sourceCount: number;
  personaCount: number;
}

/** Project detail — collections populated. */
export interface MatchProject extends MatchProjectSummary {
  sources: MatchSource[];
  personas: MatchPersonaRef[];
}

export interface MatchMatrix {
  projectId: string;
  cells: MatchCell[];
}

/** Create/update request body (subset the backend reads on write). */
export interface MatchProjectRequest {
  name: string;
  description?: string | null;
  enabled: boolean;
  schedule: MatchSchedule;
  llmInstanceId?: string | null;
}

export interface MatchSourceRequest {
  type: MatchSourceType;
  sourceName?: string;
  ref: string;
  siteName?: string | null;
}

/** SSE progress event streamed by the N×N runner. */
export interface MatchRunEvent {
  type: "STARTED" | "CELL" | "DONE" | "ERROR";
  projectId: string;
  total: number;
  completed: number;
  cell?: MatchCell | null;
  lastRunAt?: string | null;
  error?: string | null;
}

// ---------------------------------------------------------------------------
// Matrix map + report lenses (pure).
// ---------------------------------------------------------------------------

export const cellKey = (sourceId: string, personaId: string) =>
  `${sourceId}:${personaId}`;

/** Build the keyed matrix map from a flat cell list. */
export function buildMatrixMap(cells: MatchCell[]): Map<string, MatchCell> {
  const map = new Map<string, MatchCell>();
  cells.forEach((c) => map.set(cellKey(c.sourceId, c.personaId), c));
  return map;
}

/** Semantic fit level — translated at the render site (i18n). */
export type FitLevel = "high" | "medium" | "low";

export interface FitTone {
  fill: string;
  soft: string;
  text: string;
  border: string;
  /** i18n-friendly level key (`persona.match.fitTone.<level>`). */
  level: FitLevel;
}

export function fitTone(score: number): FitTone {
  if (score >= 75) {
    return {
      fill: "bg-emerald-500",
      soft: "bg-emerald-500/10",
      text: "text-emerald-700 dark:text-emerald-300",
      border: "border-emerald-500/30",
      level: "high",
    };
  }
  if (score >= 50) {
    return {
      fill: "bg-amber-500",
      soft: "bg-amber-500/10",
      text: "text-amber-700 dark:text-amber-300",
      border: "border-amber-500/30",
      level: "medium",
    };
  }
  return {
    fill: "bg-rose-500",
    soft: "bg-rose-500/10",
    text: "text-rose-700 dark:text-rose-300",
    border: "border-rose-500/30",
    level: "low",
  };
}

/** i18n key suffixes for the label maps (translate via `persona.match.*`). */
export const SOURCE_TYPES: MatchSourceType[] = ["URL", "SN_DOC", "ASSET"];
export const SCHEDULES: MatchSchedule[] = ["MANUAL", "DAILY", "WEEKLY"];

export interface RankedPersona {
  persona: MatchPersonaRef;
  cell: MatchCell;
}
export interface RankedSource {
  source: MatchSource;
  cell: MatchCell;
}

/** For each content: personas ranked by fit (best first). */
export function rankByContent(
  sources: MatchSource[],
  personas: MatchPersonaRef[],
  matrix: Map<string, MatchCell>,
): { source: MatchSource; ranked: RankedPersona[]; avg: number }[] {
  return sources.map((source) => {
    const ranked = personas
      .map((persona) => ({ persona, cell: matrix.get(cellKey(source.id, persona.id))! }))
      .filter((r) => r.cell)
      .sort((a, b) => b.cell.fitScore - a.cell.fitScore);
    const avg = ranked.length
      ? Math.round(ranked.reduce((s, r) => s + r.cell.fitScore, 0) / ranked.length)
      : 0;
    return { source, ranked, avg };
  });
}

/** For each persona: contents ranked by fit (best first). */
export function rankByPersona(
  sources: MatchSource[],
  personas: MatchPersonaRef[],
  matrix: Map<string, MatchCell>,
): { persona: MatchPersonaRef; ranked: RankedSource[]; avg: number }[] {
  return personas.map((persona) => {
    const ranked = sources
      .map((source) => ({ source, cell: matrix.get(cellKey(source.id, persona.id))! }))
      .filter((r) => r.cell)
      .sort((a, b) => b.cell.fitScore - a.cell.fitScore);
    const avg = ranked.length
      ? Math.round(ranked.reduce((s, r) => s + r.cell.fitScore, 0) / ranked.length)
      : 0;
    return { persona, ranked, avg };
  });
}

/** Overall project average fit across all cells present in the matrix. */
export function projectAvgFit(
  sources: MatchSource[],
  personas: MatchPersonaRef[],
  matrix: Map<string, MatchCell>,
): number {
  const cells: MatchCell[] = [];
  sources.forEach((s) =>
    personas.forEach((p) => {
      const c = matrix.get(cellKey(s.id, p.id));
      if (c) cells.push(c);
    }),
  );
  if (!cells.length) return 0;
  return Math.round(cells.reduce((sum, c) => sum + c.fitScore, 0) / cells.length);
}
