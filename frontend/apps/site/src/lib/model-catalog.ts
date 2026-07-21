// The public model catalog (Block BC). Imported at build time from the artifact
// the site also serves at /models/catalog.json — so the /models page renders
// fully server-side (prerendered, crawlable) with no runtime fetch, and stays
// in lockstep with the endpoint. Regenerated from the canonical source by
// scripts/emit-model-catalog.mjs before every build.
import catalogJson from "../../public/models/catalog.json";

export type ModelKind =
  | "CHAT"
  | "EMBEDDING"
  | "RERANK"
  | "IMAGE"
  | "TRANSCRIPTION"
  | "SPEECH"
  | "VIDEO"
  | "MODERATION"
  | "UNKNOWN";

export interface ModelEntry {
  id: string;
  label: string;
  kind: ModelKind;
  vendor?: string;
  contextWindow?: number;
  embeddingDimensions?: number;
  capabilities?: string[];
  deprecated?: boolean;
}

export interface ModelCatalog {
  version: number;
  lastUpdated: string;
  source?: string;
  vendors: Record<string, ModelEntry[]>;
}

export const catalog = catalogJson as unknown as ModelCatalog;

/** Canonical display order for the type filter (most common kinds first). */
export const MODEL_KINDS: ModelKind[] = [
  "CHAT",
  "EMBEDDING",
  "RERANK",
  "IMAGE",
  "TRANSCRIPTION",
  "SPEECH",
  "VIDEO",
  "MODERATION",
  "UNKNOWN",
];

export const KIND_LABEL: Record<ModelKind, string> = {
  CHAT: "Chat",
  EMBEDDING: "Embedding",
  RERANK: "Rerank",
  IMAGE: "Image",
  TRANSCRIPTION: "Transcription",
  SPEECH: "Speech",
  VIDEO: "Video",
  MODERATION: "Moderation",
  UNKNOWN: "Other",
};

/** Colour-coded pill classes per kind (light + dark), mirroring the admin picker. */
export const KIND_BADGE: Record<ModelKind, string> = {
  CHAT: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
  EMBEDDING: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300",
  RERANK: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
  IMAGE: "bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-500/15 dark:text-fuchsia-300",
  TRANSCRIPTION: "bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300",
  SPEECH: "bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300",
  VIDEO: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300",
  MODERATION: "bg-slate-200 text-slate-700 dark:bg-slate-500/20 dark:text-slate-300",
  UNKNOWN: "bg-muted text-muted-foreground",
};

/** Friendly vendor names keyed by the catalog's provider plugin type. */
export const VENDOR_LABEL: Record<string, string> = {
  openai: "OpenAI",
  anthropic: "Anthropic",
  gemini: "Google Gemini",
  "gemini-openai": "Gemini (OpenAI-compatible)",
  ollama: "Ollama",
  "openai-compatible": "OpenAI-compatible",
  bedrock: "Amazon Bedrock",
  cohere: "Cohere",
  mistral: "Mistral",
  voyage: "Voyage AI",
  "vertex-ai": "Google Vertex AI",
};

export function vendorLabel(vendor: string): string {
  return VENDOR_LABEL[vendor] ?? vendor;
}

/** Every model across all vendors, each stamped with its vendor key. */
export function allModels(): ModelEntry[] {
  return Object.entries(catalog.vendors).flatMap(([vendor, list]) =>
    list.map((m) => ({ ...m, vendor: m.vendor ?? vendor })),
  );
}

/** Human-friendly token count: 1536 → "1,536"; 128000 → "128K"; 1048576 → "1M". */
export function formatTokens(n: number): string {
  if (n >= 1_000_000) {
    const m = n / 1_048_576;
    return `${m >= 10 ? Math.round(m) : m.toFixed(m % 1 === 0 ? 0 : 1)}M`;
  }
  if (n >= 10_000) return `${Math.round(n / 1000)}K`;
  return n.toLocaleString("en-US");
}
