import { useMemo, useState, type ReactNode } from "react";

/**
 * Provenance for one retrieved RAG chunk — the structural mirror of the SDK's
 * `TurChatSource` (T292), redeclared here so `@viglet/turing-react-ui` stays
 * zero-runtime-dependency (it must not import `@viglet/turing-sdk`).
 */
export interface TuringRagSource {
  sourceId: string;
  title: string;
  url?: string | null;
  chunkIndex?: number | null;
  score?: number | null;
  keywordOnly?: boolean;
}

/** Confidence tier derived from a source's best retrieval score. */
export type TuringSourceConfidence = "high" | "medium" | "low";

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringSourceChipsClassNames {
  /** The wrapping container. */
  container?: string;
  /** The "Sources" heading row. */
  heading?: string;
  /** The list of chips. */
  list?: string;
  /** Each source chip `<button>`. */
  chip?: string;
  /** Appended to the chip while its trace panel is expanded. */
  chipExpanded?: string;
  /** Appended to the chip for keyword-only (BM25-fallback) sources. */
  chipKeyword?: string;
  /** The confidence indicator inside a chip (carries `data-confidence`). */
  confidence?: string;
  /** The expanded "why did you say this?" trace panel. */
  panel?: string;
  /** The intro caption at the top of the trace panel. */
  panelCaption?: string;
  /** Each retrieved-chunk row inside the panel. */
  chunk?: string;
  /** The chunk metadata line (index + score). */
  chunkMeta?: string;
  /** The "open source" link/anchor. */
  link?: string;
}

/** Human-readable strings — pass localized values from the host app. */
export interface TuringSourceChipsLabels {
  /** Heading above the chips. Default `"Sources"`. */
  heading?: string;
  /** Caption opening the trace panel. Default `"Why did you say this?"`. */
  why?: string;
  /** Anchor text for the source deep link. Default `"Open source"`. */
  open?: string;
  /** Prefix for a chunk row. Default `"Passage"`. */
  chunk?: string;
  /** Note shown for keyword-only matches. Default `"keyword match"`. */
  keywordOnly?: string;
  /** Accessible label tiers, keyed by confidence. */
  confidenceHigh?: string;
  confidenceMedium?: string;
  confidenceLow?: string;
}

/** Optional icon nodes; when omitted the chip shows text only (no icon dep). */
export interface TuringSourceChipsIcons {
  /** Leading icon on every chip. */
  source?: ReactNode;
  /** Indicator that a chip is expandable / expanded. */
  expand?: ReactNode;
}

export interface TuringSourceChipsProps {
  /** The RAG provenance to render (one entry per retrieved chunk). */
  sources: ReadonlyArray<TuringRagSource>;
  /**
   * Fired when a source's deep link is activated. When provided, the default
   * anchor navigation is suppressed (call `preventDefault` yourself if needed)
   * so the host can route in-app instead of a full page load.
   */
  onSourceClick?: (source: TuringRagSource) => void;
  /** Convenience alias for {@link TuringSourceChipsClassNames.container}. */
  className?: string;
  classNames?: TuringSourceChipsClassNames;
  labels?: TuringSourceChipsLabels;
  icons?: TuringSourceChipsIcons;
}

/** One source after grouping its chunks. */
interface GroupedSource {
  key: string;
  title: string;
  url?: string | null;
  bestScore: number | null;
  keywordOnly: boolean;
  chunks: TuringRagSource[];
}

/** Maps a best score to a coarse confidence tier (cosine-normalised, clamped). */
function confidenceOf(score: number | null, keywordOnly: boolean): TuringSourceConfidence {
  if (keywordOnly) return "low";
  if (score == null) return "medium";
  if (score >= 0.75) return "high";
  if (score >= 0.5) return "medium";
  return "low";
}

function groupSources(sources: ReadonlyArray<TuringRagSource>): GroupedSource[] {
  const byKey = new Map<string, GroupedSource>();
  for (const s of sources) {
    const key = s.sourceId || s.url || s.title;
    if (!key) continue;
    const existing = byKey.get(key);
    const score = typeof s.score === "number" ? s.score : null;
    if (existing) {
      existing.chunks.push(s);
      if (score != null && (existing.bestScore == null || score > existing.bestScore)) {
        existing.bestScore = score;
      }
      // A source is "keyword only" iff every chunk reached it via BM25.
      existing.keywordOnly = existing.keywordOnly && Boolean(s.keywordOnly);
    } else {
      byKey.set(key, {
        key,
        title: s.title || s.sourceId,
        url: s.url,
        bestScore: score,
        keywordOnly: Boolean(s.keywordOnly),
        chunks: [s],
      });
    }
  }
  return [...byKey.values()];
}

/**
 * Headless source-chip renderer for RAG provenance (T293 / T332). Renders one
 * chip per cited source with a confidence cue, and a one-click "why did you say
 * this?" trace that expands to the exact retrieved passages (with chunk index,
 * score, and a deep link).
 *
 * <p>Provider-agnostic: it consumes Turing's own retrieval metadata (the SDK's
 * `TurChatSource[]`), so it works on every LLM with no vendor citation API.
 * Like the other `@viglet/turing-react-ui` primitives it ships no styling —
 * skin every slot via `className`/`classNames`, localize via `labels`, and plug
 * in `icons` from whatever set the host uses. The admin console and the public
 * SN AI-mode chat share this one implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringSourceChips({
  sources,
  onSourceClick,
  className,
  classNames,
  labels,
  icons,
}: Readonly<TuringSourceChipsProps>) {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const groups = useMemo(() => groupSources(sources), [sources]);

  if (groups.length === 0) return null;

  const headingLabel = labels?.heading ?? "Sources";
  const whyLabel = labels?.why ?? "Why did you say this?";
  const openLabel = labels?.open ?? "Open source";
  const chunkLabel = labels?.chunk ?? "Passage";
  const keywordLabel = labels?.keywordOnly ?? "keyword match";

  const confidenceLabel = (c: TuringSourceConfidence): string => {
    if (c === "high") return labels?.confidenceHigh ?? "High confidence";
    if (c === "medium") return labels?.confidenceMedium ?? "Medium confidence";
    return labels?.confidenceLow ?? "Low confidence";
  };

  const toggle = (key: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const handleLinkClick = (source: TuringRagSource, e: React.MouseEvent) => {
    if (onSourceClick) {
      e.preventDefault();
      onSourceClick(source);
    }
  };

  return (
    <div className={className ?? classNames?.container} data-turing-source-chips="">
      <div className={classNames?.heading}>{headingLabel}</div>
      <div className={classNames?.list}>
        {groups.map((group) => {
          const isOpen = expanded.has(group.key);
          const confidence = confidenceOf(group.bestScore, group.keywordOnly);
          const chipClass = [
            classNames?.chip,
            isOpen ? classNames?.chipExpanded : undefined,
            group.keywordOnly ? classNames?.chipKeyword : undefined,
          ]
            .filter(Boolean)
            .join(" ");
          return (
            <div key={group.key} data-source-key={group.key}>
              <button
                type="button"
                className={chipClass || undefined}
                onClick={() => toggle(group.key)}
                aria-expanded={isOpen}
                title={confidenceLabel(confidence)}
                data-confidence={confidence}
                data-keyword-only={group.keywordOnly ? "" : undefined}
              >
                {icons?.source}
                <span>{group.title}</span>
                <span
                  className={classNames?.confidence}
                  data-confidence={confidence}
                  aria-label={confidenceLabel(confidence)}
                />
                {icons?.expand}
              </button>
              {isOpen && (
                <div className={classNames?.panel} role="region">
                  <div className={classNames?.panelCaption}>{whyLabel}</div>
                  {group.chunks.map((chunk, i) => (
                    <div className={classNames?.chunk} key={`${group.key}-${chunk.chunkIndex ?? i}`}>
                      <span className={classNames?.chunkMeta}>
                        {chunk.chunkIndex != null ? `${chunkLabel} #${chunk.chunkIndex}` : chunkLabel}
                        {typeof chunk.score === "number" ? ` · ${chunk.score.toFixed(2)}` : ""}
                        {chunk.keywordOnly ? ` · ${keywordLabel}` : ""}
                      </span>
                      {chunk.url ? (
                        <a
                          className={classNames?.link}
                          href={chunk.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          onClick={(e) => handleLinkClick(chunk, e)}
                        >
                          {openLabel}
                        </a>
                      ) : null}
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
