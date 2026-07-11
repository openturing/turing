/**
 * T662–T666 / Block AP — engine-agnostic synonym rule and the engine
 * capability/apply shapes the admin UI works with.
 */

export type TurSNSynonymType =
  | "REGULAR"
  | "ONE_WAY"
  | "ALTERNATIVE_CORRECTION_1"
  | "ALTERNATIVE_CORRECTION_2"
  | "PLACEHOLDER";

export interface TurSNSynonym {
  id?: string;
  name?: string | null;
  type: TurSNSynonymType;
  language: string;
  input?: string | null;
  terms: string[];
  enabled: boolean;
}

/** Whether the site's resolved engine supports query-time synonyms. */
export interface TurSNSynonymSupport {
  engine: string;
  supported: boolean;
}

/** Outcome of pushing rules into an engine (per locale). */
export interface TurSESynonymApplyResult {
  supported: boolean;
  applied: number;
  unsupportedTypes: TurSNSynonymType[];
  warnings: string[];
}

export interface TurSNSynonymAlgoliaImportRequest {
  appId: string;
  apiKey: string;
  host?: string;
  index: string;
  language: string;
}
