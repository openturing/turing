import type { CSSProperties, ReactNode } from "react";

/**
 * One flagged span from a content-fit verdict (<em>não condiz</em>) — the
 * structural mirror of the SDK's `TurContentFitMisfit` (T634/T635), redeclared
 * here so `@viglet/turing-react-ui` stays zero-runtime-dependency.
 */
export interface TuringContentFitMisfit {
  span: string;
  reason: string;
  suggestion: string;
}

/**
 * A "validate this content as persona X" verdict — the structural mirror of the
 * SDK's `TurContentFit` (T634/T635), redeclared here so the package stays
 * zero-runtime-dependency (the caller fetches it via
 * `fetchPersonaContentFit` and passes the result down as a prop).
 */
export interface TuringContentFitResult {
  personaId: string;
  personaName: string;
  /** Overall fit on a 0–100 scale (already a percentage; matches the backend). */
  fitScore: number;
  summary: string;
  fits: string[];
  misfits: TuringContentFitMisfit[];
  /** `false` when the LLM was unavailable and only the readability score applies. */
  llmUsed: boolean;
}

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringContentFitClassNames {
  /** The wrapping container. */
  container?: string;
  /** The header row (persona name + fit %). */
  header?: string;
  /** The persona name. */
  personaName?: string;
  /** The numeric fit-% value. */
  fitValue?: string;
  /** The fit-bar track (carries `role=progressbar`). */
  fitBar?: string;
  /** The fit-bar fill (carries `data-fit` + a `--turing-ui-fit` width var). */
  fitBarFill?: string;
  /** The verdict summary paragraph. */
  summary?: string;
  /** The "what fits" heading. */
  fitsHeading?: string;
  /** The list of fitting points (<em>condiz</em>). */
  fits?: string;
  /** Each fitting-point item. */
  fitItem?: string;
  /** The "what doesn't fit" heading. */
  misfitsHeading?: string;
  /** The list of flagged spans (<em>não condiz</em>). */
  misfits?: string;
  /** Each flagged-span item (carries `data-reason`). */
  misfitItem?: string;
  /** The offending span text. */
  misfitSpan?: string;
  /** The reason the span doesn't fit. */
  misfitReason?: string;
  /** The suggested rewrite. */
  misfitSuggestion?: string;
  /** The note shown when the verdict is readability-only (LLM unavailable). */
  fallbackNote?: string;
}

/** Human-readable strings — pass localized values from the host app. */
export interface TuringContentFitLabels {
  /** Accessible label for the fit bar. Default `"Content fit"`. */
  heading?: string;
  /** Heading above the fitting points. Default `"What fits"`. */
  fitsHeading?: string;
  /** Heading above the flagged spans. Default `"What doesn't fit"`. */
  misfitsHeading?: string;
  /** Note shown when `llmUsed` is false. Default a readability-only caption. */
  fallbackNote?: string;
}

/** Optional icon nodes; when omitted the lists show text only (no icon dep). */
export interface TuringContentFitIcons {
  /** Leading icon on each fitting point. */
  fit?: ReactNode;
  /** Leading icon on each flagged span. */
  misfit?: ReactNode;
}

export interface TuringContentFitProps {
  /** The verdict to render (from the SDK's `fetchPersonaContentFit`). */
  result: TuringContentFitResult;
  /** Convenience alias for {@link TuringContentFitClassNames.container}. */
  className?: string;
  classNames?: TuringContentFitClassNames;
  labels?: TuringContentFitLabels;
  icons?: TuringContentFitIcons;
}

/** Clamps a raw fit score to 0–100 (defends against out-of-range LLM output). */
function clampFit(score: number): number {
  if (Number.isNaN(score)) return 0;
  if (score < 0) return 0;
  if (score > 100) return 100;
  return score;
}

/**
 * Headless content-fit verdict renderer (T636) — "validate this content as
 * persona X". Shows the overall fit % (as an accessible progress bar), a short
 * summary, what does fit (<em>condiz</em>), the flagged spans that don't
 * (<em>não condiz</em>, each with a reason + suggested rewrite), and a note when
 * the score is readability-only (LLM unavailable).
 *
 * <p>Pure renderer: it takes the {@link TuringContentFitResult} via props (the
 * caller fetches it with the SDK's `fetchPersonaContentFit`) and touches no hook
 * or API, so it carries no SDK/axios coupling. Ships no styling — skin every
 * slot via `className`/`classNames`, localize via `labels`, plug in `icons`. The
 * fit bar exposes both `data-fit` (0..100) and a `--turing-ui-fit` CSS width
 * variable so the host draws the fill. Admin console + public demo share it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function TuringContentFit({
  result,
  className,
  classNames,
  labels,
  icons,
}: Readonly<TuringContentFitProps>) {
  const pct = Math.round(clampFit(result.fitScore));
  const fitsHeading = labels?.fitsHeading ?? "What fits";
  const misfitsHeading = labels?.misfitsHeading ?? "What doesn't fit";
  const fillStyle = { ["--turing-ui-fit"]: `${pct}%` } as CSSProperties;

  return (
    <div
      className={className ?? classNames?.container}
      data-turing-content-fit=""
      data-llm-used={result.llmUsed ? "" : undefined}
    >
      <div className={classNames?.header}>
        <span className={classNames?.personaName}>{result.personaName}</span>
        <span className={classNames?.fitValue} data-fit={pct}>{pct}%</span>
      </div>
      <div
        className={classNames?.fitBar}
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={labels?.heading ?? "Content fit"}
      >
        <span className={classNames?.fitBarFill} data-fit={pct} style={fillStyle} />
      </div>
      {result.summary ? <p className={classNames?.summary}>{result.summary}</p> : null}
      {result.fits.length > 0 && (
        <div>
          <div className={classNames?.fitsHeading}>{fitsHeading}</div>
          <ul className={classNames?.fits}>
            {result.fits.map((fit, i) => (
              <li key={`${i}-${fit}`} className={classNames?.fitItem}>
                {icons?.fit}
                {fit}
              </li>
            ))}
          </ul>
        </div>
      )}
      {result.misfits.length > 0 && (
        <div>
          <div className={classNames?.misfitsHeading}>{misfitsHeading}</div>
          <ul className={classNames?.misfits}>
            {result.misfits.map((misfit, i) => (
              <li
                key={`${i}-${misfit.span}`}
                className={classNames?.misfitItem}
                data-reason={misfit.reason}
              >
                {icons?.misfit}
                <span className={classNames?.misfitSpan}>{misfit.span}</span>
                <span className={classNames?.misfitReason}>{misfit.reason}</span>
                {misfit.suggestion ? (
                  <span className={classNames?.misfitSuggestion}>{misfit.suggestion}</span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      )}
      {!result.llmUsed && (
        <div className={classNames?.fallbackNote}>
          {labels?.fallbackNote ?? "Readability score only — AI verdict unavailable."}
        </div>
      )}
    </div>
  );
}
