import type { TurAIAgent } from "../agent/ai-agent.model.ts";
import type { TurSEInstance } from "../se/se-instance.model.ts";

/**
 * T24b — BM25 source for hybrid retrieval. `EMBEDDED` keeps the T24 base
 * path (single in-process Lucene index); `SE_INSTANCE` routes BM25 to
 * per-locale Solr / Elasticsearch cores via {@link TurSEInstance}.
 */
export type TurRagBm25Source = "EMBEDDED" | "SE_INSTANCE";

/**
 * T383 / §XX.3 — ranking mode for the PUBLIC SN search (faceted catalog
 * results), distinct from the RAG flags which only affect the chat
 * knowledge-base tool. `LEGACY` (default) is lexical-only; `HYBRID_RRF`
 * fuses BM25 + a vector ranking of the same documents via Reciprocal Rank
 * Fusion (k=60) when the sort is relevance. `HYBRID_RRF_RERANK` (T389)
 * additionally re-orders the fused page through the Block N reranker strategy
 * seam (LLM | CROSS_ENCODER | COHERE, selected in Global Settings).
 */
export type TurSNRankingMode = "LEGACY" | "HYBRID_RRF" | "HYBRID_RRF_RERANK";

export interface TurSNSiteGenAi {
  id: string;
  /**
   * AI Agent that powers this site's GenAI features. The agent owns the
   * RAG enabled flag, LLM list, embedding model, vector store AND the
   * system prompt used by the chat. When null, GenAI is disabled for the
   * site.
   */
  turAIAgent?: TurAIAgent | null;
  /**
   * Site description prompt used by the summary/insights LLM call. This is
   * NOT the chat system prompt (that one lives on the agent).
   */
  sitePrompt?: string;
  /**
   * T19 / §III.3 (repositioned 2026.2.7) — opt-out for the BM25 emergency
   * fallback in `search_knowledge_base`. When `true` (default), low-
   * confidence vector misses retry as BM25 and the result is tagged
   * "keyword-only" so the LLM softens its answer. When `false`, vector
   * misses return "no results" directly. Moved here from `TurAIAgent`
   * because RAG only runs in SN site context. @since 2026.2.7
   */
  ragBm25Fallback?: boolean;
  /**
   * T24 / §III.2 (repositioned 2026.2.7) — opt-in for hybrid retrieval
   * (vector + BM25 fused via RRF). Effective only when `ragBm25Fallback`
   * is also `true`. Default `true`. @since 2026.2.7
   */
  ragHybridSearch?: boolean;
  /**
   * T24b / §III.2 — BM25 source for hybrid retrieval. `EMBEDDED` (default)
   * uses a single in-process Lucene index for every locale; `SE_INSTANCE`
   * routes BM25 to per-locale Solr / Elasticsearch cores on
   * {@link ragSeInstance} (one core per SN site locale).
   * @since 2026.2.7
   */
  ragBm25Source?: TurRagBm25Source;
  /**
   * T24b / §III.2 — the SE instance hosting per-locale BM25 cores when
   * {@link ragBm25Source} is `SE_INSTANCE`. Null on the embedded path.
   * @since 2026.2.7
   */
  ragSeInstance?: TurSEInstance | null;
  /**
   * T383 / §XX.3 — ranking mode for the public SN search. `LEGACY` (default)
   * keeps lexical-only ranking; `HYBRID_RRF` opts into BM25 + vector RRF
   * fusion; `HYBRID_RRF_RERANK` (T389) adds the reranker stage on top.
   * Independent of the `rag*` flags above. @since 2026.3.1
   */
  snRankingMode?: TurSNRankingMode;
  /**
   * T472 / §XXVI.9 — opt-in for the index-time content-fit (readability)
   * signal. When `true` and {@link contentFitPersonaId} resolves to an
   * AUDIENCE/BOTH persona, each document is scored against that audience as it
   * is indexed and the 0–100 fit score is stored as the `content_fit_score`
   * SN field, surfaced by the content-fit coverage report. Default `false`
   * keeps the indexing path unchanged. @since 2026.3.4
   */
  contentFitIndexingEnabled?: boolean;
  /**
   * T472 / §XXVI.9 — target-audience `TurPersona` id read as the reader proxy
   * by the index-time scorer. Null/blank disables the signal even when
   * {@link contentFitIndexingEnabled} is `true`. @since 2026.3.4
   */
  contentFitPersonaId?: string | null;
  /**
   * T501 / §X.19 — opt-in for index-time native video/audio understanding. When
   * `true` and the site's GenAI agent resolves to a Gemini LLM, a document that
   * references a video/audio asset (a `media_url` attribute, or a media-typed
   * `url`) is run through Gemini understanding as it is indexed and the resulting
   * timestamped transcript + scene description is appended to the document's text
   * field — so the clip's content becomes searchable. Gemini-only, fail-open;
   * spends one LLM call per media document. Default `false` keeps indexing
   * unchanged. @since 2026.3.4
   */
  mediaUnderstandingIndexingEnabled?: boolean;
}

/**
 * T24b / §III.2 — one row in the per-locale BM25 core status table shown
 * to the admin when {@code ragBm25Source = SE_INSTANCE}. Mirrors the
 * `CoreStatusDto` returned by `GET /api/sn/{siteId}/rag/cores`.
 *
 * @since 2026.2.7
 */
export interface TurRagBm25CoreStatus {
  /** IETF BCP 47 tag (e.g. `pt-BR`, `en-US`). */
  locale: string;
  /** Auto-derived core name (`rag_<storeShortId>_<locale>`). */
  coreName: string;
  /**
   * Lifecycle: `NOT_PROVISIONED` (row missing in SE), `PROVISIONING`,
   * `PROVISIONED`, `ERROR`, `DELETING`. The UI maps these to badge
   * colours; only PROVISIONED rows can serve queries.
   */
  status:
    | "NOT_PROVISIONED"
    | "PROVISIONING"
    | "PROVISIONED"
    | "ERROR"
    | "DELETING";
  /** Denormalized indexed-document count (may lag SE by seconds). */
  docCount: number;
  /** Captured message from the last failed provisioning, if any. */
  lastError?: string | null;
  /** Whether a row exists in `rag_bm25_core` (false → never provisioned). */
  provisioned: boolean;
}
