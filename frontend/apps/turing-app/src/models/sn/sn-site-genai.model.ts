import type { TurAIAgent } from "../agent/ai-agent.model.ts";
import type { TurSEInstance } from "../se/se-instance.model.ts";

/**
 * T24b — BM25 source for hybrid retrieval. `EMBEDDED` keeps the T24 base
 * path (single in-process Lucene index); `SE_INSTANCE` routes BM25 to
 * per-locale Solr / Elasticsearch cores via {@link TurSEInstance}.
 */
export type TurRagBm25Source = "EMBEDDED" | "SE_INSTANCE";

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
