/**
 * T472 / §XXVI.9 — index-time audience content-fit coverage for an SN site.
 * Mirrors the backend `TurSNContentFitReport` record. Computed by range-counting
 * the `content_fit_score` field written at index time.
 */
export interface TurSNContentFitReport {
  siteId: string;
  siteName: string;
  /** Whether index-time content-fit scoring is switched on for the site. */
  enabled: boolean;
  /** Target-audience persona id (null when unset). */
  personaId: string | null;
  /** Target-audience persona name (null when unset). */
  personaName: string | null;
  totalDocuments: number;
  /** Documents carrying a `content_fit_score`. */
  scoredDocuments: number;
  /** Documents whose score is below `tooComplexThreshold` (red). */
  tooComplex: number;
  /** Documents in [`tooComplexThreshold`, `goodThreshold`) (amber). */
  borderline: number;
  /** Documents at or above `goodThreshold` (green). */
  good: number;
  tooComplexThreshold: number;
  goodThreshold: number;
  /** `tooComplex / scoredDocuments * 100`, one decimal. */
  tooComplexPercent: number;
  /** False when the search engine can't range-count the score field. */
  supported: boolean;
}
