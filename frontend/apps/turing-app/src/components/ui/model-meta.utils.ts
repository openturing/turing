/**
 * T1024 — the pure half of the model-meta module.
 *
 * Split out because the file exported four components beside these helpers,
 * and a module that exports both reloads whole instead of preserving state.
 * Nothing here renders: they read catalog metadata and answer a question about
 * it, which is why they are the half that moved.
 */
import type {
  TurLlmModelMetadata,
  TurLlmModelOption,
} from "@/models/llm/llm-model-option.model.ts"


/** How a picker list is ordered. */
export type ModelSortValue = "relevance" | "price" | "context" | "intelligence"

export const MODEL_SORT_VALUES: ModelSortValue[] = ["relevance", "price", "context", "intelligence"]
/**
 * T782 — is the model end-of-life per the catalog (`deprecated` flag, or
 * `status` DEPRECATED/RETIRED)? Used to badge it in the picker.
 */
export function isDeprecatedModel(metadata?: TurLlmModelMetadata | null): boolean {
  if (!metadata) return false
  if (metadata.deprecated) return true
  const status = metadata.status?.trim().toUpperCase()
  return status === "DEPRECATED" || status === "RETIRED"
}
/** True when at least one option carries metadata worth sorting/enriching by. */
export function hasEnrichedMetadata(options: TurLlmModelOption[]): boolean {
  return options.some((o) => o.metadata != null)
}
// T781 — catalog `modalities.input` slugs that mean the model accepts a
// file/image on input, so file-upload can safely be enabled.
const FILE_INPUT_MODALITIES = new Set(["image", "file", "pdf", "document", "audio", "video"])

/**
 * T781 — does the catalog say the model supports tool calling? `undefined` when
 * unknown (no metadata, or the entry lists no capabilities) so callers can avoid
 * warning on a guess — only on positive evidence the model lacks it.
 */
export function catalogSupportsTools(meta?: TurLlmModelMetadata | null): boolean | undefined {
  if (!meta?.capabilities?.length) return undefined
  return meta.capabilities.some((c) => c.toLowerCase() === "tools")
}

/**
 * T781 — does the catalog say the model can accept file/image input (the `vision`
 * capability or a file-like input modality)? `undefined` when the entry carries
 * neither capabilities nor modalities (unknown → no warning).
 */
export function catalogSupportsFileInput(meta?: TurLlmModelMetadata | null): boolean | undefined {
  if (!meta) return undefined
  const caps = (meta.capabilities ?? []).map((c) => c.toLowerCase())
  const inputs = (meta.modalities?.input ?? []).map((m) => m.toLowerCase())
  if (caps.length === 0 && inputs.length === 0) return undefined
  return caps.includes("vision") || inputs.some((m) => FILE_INPUT_MODALITIES.has(m))
}
/** Numeric key a sort reads from an option, or `undefined` when unknown. */
function sortKey(option: TurLlmModelOption, sort: ModelSortValue): number | undefined {
  const m = option.metadata
  if (!m) return undefined
  switch (sort) {
    case "price":
      return m.pricing?.inputPer1M ?? undefined
    case "context":
      return m.contextWindow ?? undefined
    case "intelligence":
      return m.benchmarks?.intelligenceIndex ?? undefined
    default:
      return undefined
  }
}

/**
 * Returns a new list ordered by the chosen sort. `relevance` keeps the incoming
 * order. `price` is ascending (cheapest first); `context` and `intelligence` are
 * descending (biggest/best first). Options missing the metric always sink to the
 * end so the list never hides a model, and ties keep their original order.
 */
export function sortModelOptions(options: TurLlmModelOption[], sort: ModelSortValue): TurLlmModelOption[] {
  if (sort === "relevance") return options
  const descending = sort !== "price"
  return options
    .map((option, index) => ({ option, index, key: sortKey(option, sort) }))
    .sort((a, b) => {
      if (a.key == null && b.key == null) return a.index - b.index
      if (a.key == null) return 1
      if (b.key == null) return -1
      if (a.key === b.key) return a.index - b.index
      return descending ? b.key - a.key : a.key - b.key
    })
    .map((entry) => entry.option)
}
