/**
 * Types for the SN site field-manifest endpoints (T382 / T386 / T387).
 *
 * - {@link TurSNManifestDeriveRequest} → `POST /api/sn/manifest/derive` (T387):
 *   a sample of a source's documents in, a **draft** manifest out (for review).
 * - {@link TurSNSiteManifest} → `POST /api/sn/manifest` (T382/T386): the reviewed
 *   manifest, converged into the live site idempotently.
 */

/** Per-field spec — mirrors the backend `TurSNJobAttributeSpec`. `type` is a `TurSEFieldType` enum name. */
export type TurSNManifestFieldSpec = {
  name: string;
  /** TurSEFieldType enum name: INT | LONG | STRING | TEXT | ARRAY | DATE | BOOL | FLOAT | DOUBLE | CURRENCY */
  type: string;
  mandatory: boolean;
  multiValued: boolean;
  facet: boolean;
  description?: string | null;
  facetName?: Record<string, string> | null;
};

/** A declarative SN site + field schema — the draft returned by derive, and the body provision converges. */
export type TurSNSiteManifest = {
  name: string;
  description?: string | null;
  seInstanceId?: string | null;
  schemaVersion?: string | null;
  locales?: string[] | null;
  fields: TurSNManifestFieldSpec[];
  migrations?: unknown[] | null;
};

/** Body for `POST /api/sn/manifest/derive`: site identity + the sample documents to infer a schema from. */
export type TurSNManifestDeriveRequest = {
  name: string;
  description?: string | null;
  seInstanceId?: string | null;
  locales?: string[] | null;
  /** Sample documents (parsed JSON objects) the draft schema is inferred from. Required, non-empty. */
  documents: Record<string, unknown>[];
};

/** Outcome of `POST /api/sn/manifest`: what converged (re-running the same manifest is a no-op). */
export type TurSNManifestResult = {
  siteId: string;
  siteName: string;
  siteCreated: boolean;
  fieldsCreated: string[];
  fieldsSkipped: string[];
  fieldsMigrated: string[];
  localesCreated: string[];
  schemaVersion?: string | null;
};
