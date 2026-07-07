/**
 * Block AA / §XXVI.2 — one evaluation source in a persona's "notebook".
 * Mirrors the backend `TurPersonaSource`. The raw extracted text never crosses
 * the wire; the admin sees `cachedTextPreview` + `cachedTextLength` instead.
 */
export type TurPersonaSourceType = "SN_DOC" | "ASSET" | "URL";
export type TurPersonaSourceStatus = "PENDING" | "EXTRACTED" | "FAILED";

export interface TurPersonaSource {
  id: string;
  type: TurPersonaSourceType;
  sourceName?: string | null;
  /** SN document id (SN_DOC), original filename (ASSET), or the URL (URL). */
  ref?: string | null;
  /** Only for SN_DOC — the Semantic Navigation site the document lives in. */
  siteName?: string | null;
  extractedAt?: string | null;
  extractionStatus: TurPersonaSourceStatus;
  extractionError?: string | null;
  cachedTextPreview?: string | null;
  cachedTextLength?: number;
}

/** Payload to add an SN_DOC or URL source (ASSET goes through upload). */
export interface TurPersonaSourceCreate {
  type: "SN_DOC" | "URL";
  sourceName?: string | null;
  ref: string;
  siteName?: string | null;
}
