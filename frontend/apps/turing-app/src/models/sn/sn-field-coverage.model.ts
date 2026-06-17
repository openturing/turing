/**
 * T388 — per-field coverage / completeness observability for an SN site.
 * Mirrors the backend `TurSNFieldCoverageReport` / `TurSNFieldCoverage` records.
 */

export interface TurSNFieldCoverage {
  fieldName: string;
  fieldType: string | null;
  multiValued: boolean;
  facet: boolean;
  /** Documents populating the field, or -1 when the engine can't report it. */
  presentDocuments: number;
  totalDocuments: number;
  /** 0–100, one decimal. Meaningless (0) when `supported` is false. */
  coveragePercent: number;
  /** False when the search engine can't count field presence. */
  supported: boolean;
}

export interface TurSNFieldCoverageReport {
  siteId: string;
  siteName: string;
  totalDocuments: number;
  /** True when at least one field could be measured against this engine. */
  supported: boolean;
  fields: TurSNFieldCoverage[];
}
