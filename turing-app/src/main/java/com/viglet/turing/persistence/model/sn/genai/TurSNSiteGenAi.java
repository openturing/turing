/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.model.sn.genai;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.rag.TurRagBm25Source;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * GenAI binding for an SN site. The site delegates LLM, embedding model,
 * vector store and the RAG enabled flag to a referenced {@link TurAIAgent} —
 * only the site-specific prompts (system prompt and site description prompt)
 * remain here.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site_genai")
@EntityListeners(TurSNSiteSnapshotEvictionListener.class)
public class TurSNSiteGenAi implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * AI agent that powers this site's GenAI features. When null, GenAI is
     * effectively disabled for the site. The agent's {@code ragEnabled},
     * embedding model, store, LLM list and {@code systemPrompt} are the
     * source of truth.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private TurAIAgent turAIAgent;

    /**
     * Site description used as input to the summary/insights LLM call (a
     * different feature from the chat). Distinct from the agent's system
     * prompt, which drives the chat itself.
     */
    @Lob
    @Column
    private String sitePrompt;

    /**
     * T19 / §III.3 (repositioned 2026.2.7) — opt-out for the BM25 emergency
     * fallback in {@code search_knowledge_base}. When {@code true} (default),
     * if the vector top-1 cosine score is below {@code MIN_VECTOR_CONFIDENCE},
     * {@link com.viglet.turing.genai.tool.TurRagSearchToolService} retries
     * the query as BM25 and prepends a keyword-only warning header. When
     * {@code false}, vector misses return "no results" directly.
     *
     * <p>Lives on {@code TurSNSiteGenAi} (not {@code TurAIAgent}) because
     * RAG is only invoked through the SN site chat API — the SN site is
     * the natural owner of the retrieval-mode config, since it also
     * owns the locale via {@code TurSNSiteLocale}.
     *
     * @since 2026.2.7
     */
    @Column(name = "ragBm25Fallback", nullable = false)
    private boolean ragBm25Fallback = true;

    /**
     * T24 / §III.2 (repositioned 2026.2.7) — when {@code true} (default)
     * AND {@link #ragBm25Fallback} is also {@code true}, RAG retrieval
     * runs vector + BM25 in parallel and fuses via Reciprocal Rank Fusion
     * ({@code k=60}). When {@code false}, BM25 is only triggered as an
     * emergency fallback (T19 semantics). When {@code ragBm25Fallback}
     * itself is {@code false}, this flag is ignored — strict vector only.
     *
     * <p>Locale-aware BM25 cores (the T24b production path) will use the
     * SN site's {@code TurSNSiteLocale} mappings to route queries to the
     * right per-locale core. Until T24b ships, the embedded Lucene
     * single-collection path serves both modes via
     * {@code TurLuceneVectorStore.hybridSearch}.
     *
     * @since 2026.2.7
     */
    @Column(name = "ragHybridSearch", nullable = false)
    private boolean ragHybridSearch = true;

    /**
     * T24b / §III.2 — picks where the BM25 half of hybrid retrieval lives:
     * {@link TurRagBm25Source#EMBEDDED} (default — single in-process
     * Lucene index, T24 base path) or {@link TurRagBm25Source#SE_INSTANCE}
     * (per-locale cores in {@link #ragSeInstance}, T24b production path).
     *
     * <p>Effective only when {@link #ragBm25Fallback} and
     * {@link #ragHybridSearch} are both {@code true}. Otherwise ignored
     * — strict-vector and fallback-only paths never touch BM25 at all.
     *
     * @since 2026.2.7
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ragBm25Source", nullable = false, length = 16)
    private TurRagBm25Source ragBm25Source = TurRagBm25Source.EMBEDDED;

    /**
     * T24b / §III.2 — the search engine instance hosting the per-locale
     * BM25 cores when {@link #ragBm25Source} is {@code SE_INSTANCE}.
     * Null when running on the embedded path. Set from the admin UI's
     * SE selector (Phase 4).
     *
     * <p>Same {@link TurSEInstance} the admin uses for SN site search
     * is a natural reuse — RAG cores get the {@code rag_} prefix to
     * stay isolated from public search ({@code TurSNSiteSearchAPI}
     * filters those out).
     *
     * @since 2026.2.7
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rag_se_instance_id")
    private TurSEInstance ragSeInstance;

    /**
     * T383 / §XX.3 — ranking mode for the <strong>public</strong> SN search
     * (the faceted catalog results), distinct from the RAG flags above which
     * only affect the chat {@code search_knowledge_base} tool.
     *
     * <p>{@link TurSNRankingMode#LEGACY} (default) keeps the historical
     * lexical-only ranking — existing sites need no revalidation.
     * {@link TurSNRankingMode#HYBRID_RRF} fuses the lexical (BM25) ranking with
     * a vector ranking of the same documents via Reciprocal Rank Fusion
     * ({@code k=60}), reusing {@link com.viglet.turing.genai.rag.TurRagRrf} and
     * the embedded vector store the RAG path already builds. Effective only when
     * the request sort is relevance; facet counts and pagination stay BM25-derived.
     * {@link TurSNRankingMode#HYBRID_RRF_RERANK} (T389) additionally re-orders the
     * fused page through the Block N {@link com.viglet.turing.genai.rag.TurRagReranker}
     * strategy seam.
     *
     * @since 2026.3.1
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "snRankingMode", nullable = false, length = 32)
    private TurSNRankingMode snRankingMode = TurSNRankingMode.LEGACY;

    /**
     * T790 / §LIV.1 (Block BF) — the site's <strong>knowledge-base mode</strong>:
     * the explicitly-named retrieval strategy that grounds its GenAI answers.
     * {@link TurSNKnowledgeBaseMode#VECTOR} (default) is the classic embedding RAG
     * chat and needs a full embedding-model + vector-store setup;
     * {@link TurSNKnowledgeBaseMode#VECTORLESS_STRUCTURED} is the T392 catalog
     * copilot (NL→DSL over the declared field schema, no embeddings) and needs only
     * a default LLM; {@link TurSNKnowledgeBaseMode#HYBRID} exposes both on the same
     * site.
     *
     * <p>Default {@code VECTOR} preserves every pre-T790 site exactly — the field
     * only changes which readiness/discoverability surface the admin sees; the
     * copilot endpoint ({@code /api/sn/{site}/copilot}) has always worked
     * regardless of this flag. Setting {@code VECTORLESS_STRUCTURED} lets the
     * AI-Mode readiness check advertise the site as ready with only a default LLM
     * (not {@code MISSING_EMBEDDING}/{@code MISSING_STORE}).
     *
     * @since 2026.3.4
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "knowledgeBaseMode", nullable = false, length = 32)
    private TurSNKnowledgeBaseMode knowledgeBaseMode = TurSNKnowledgeBaseMode.VECTOR;

    /**
     * T472 / §XXVI.9 — opt-in for the index-time content-fit signal. When
     * {@code true} (and {@link #contentFitPersonaId} resolves to an
     * {@code AUDIENCE}/{@code BOTH} persona), each document is scored at index
     * time by the deterministic
     * {@link com.viglet.turing.genai.persona.readability.TurReadabilityScorer}
     * against that audience and the 0–100 fit score is stored as the
     * {@code content_fit_score} SN field. The
     * {@code GET /api/sn/{id}/content-fit} coverage report then surfaces "content
     * too complex for its audience" alongside the T388 field-coverage dashboard.
     *
     * <p>Default {@code false} keeps the indexing path byte-for-byte unchanged —
     * the field is never provisioned and no scorer runs. Deterministic-only at
     * index time on purpose: no LLM call per document (the on-demand LLM verdict
     * stays the T467 {@code /content-fit} report), so indexing stays fast and
     * spends no credits.
     *
     * @since 2026.3.4
     */
    @Column(name = "contentFitIndexingEnabled", nullable = false)
    private boolean contentFitIndexingEnabled = false;

    /**
     * T472 / §XXVI.9 — the target-audience persona ({@code TurPersona} id) the
     * index-time scorer reads as the reader proxy. Plain id column (not a
     * {@code @ManyToOne}) so the indexing path resolves it via the cached
     * {@code TurPersonaRepository.findById} without dragging a LAZY association
     * across the queue-consumer transaction boundary. Null / blank disables the
     * signal even when {@link #contentFitIndexingEnabled} is {@code true}.
     *
     * @since 2026.3.4
     */
    @Column(name = "contentFitPersonaId", length = 40)
    private String contentFitPersonaId;

    /**
     * T500 / §X.19 — opt-in for full-context (retrieval-free) answering. When
     * {@code true} and the whole indexed corpus fits {@link #fullContextTokenBudget}
     * estimated tokens, the SN RAG path skips top-K retrieval and grounds the
     * answer over <em>every</em> chunk — inverting the RAG assumption, so recall
     * is 100% by construction for small sites. Most valuable on a 1–2 M-token
     * model window (Gemini), paired with the T499 context cache so the corpus is
     * billed once. When the corpus exceeds the budget the path falls back to the
     * normal top-K retrieval (and the T383 ranking mode) unchanged.
     *
     * <p>Default {@code false} keeps the answering path byte-for-byte unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "fullContextAnsweringEnabled", nullable = false)
    private boolean fullContextAnsweringEnabled = false;

    /**
     * T500 / §X.19 — the maximum estimated corpus size (in {@code chars/4} tokens)
     * that still qualifies for full-context answering. Above it the site falls
     * back to top-K retrieval. Default 800 000 — comfortably inside Gemini's
     * 1 M-token window with headroom for the system prompt + history + answer.
     *
     * @since 2026.3.4
     */
    @Column(name = "fullContextTokenBudget", nullable = false)
    private int fullContextTokenBudget = 800_000;

    /**
     * T501 / §X.19 — opt-in for index-time native video/audio understanding. When
     * {@code true} and the site's GenAI agent resolves to a <em>Gemini</em> LLM
     * instance, a document that references a video or audio asset (a media URL in
     * its {@code media_url} attribute, or a media-typed {@code url}) is run through
     * {@link com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService}
     * at index time and the resulting timestamped transcript + scene description is
     * appended to the document's text field — so the spoken/visual content of the
     * clip becomes searchable through the normal full-text path. A media-catalog
     * differentiator OpenAI/Anthropic can't match.
     *
     * <p>Default {@code false} keeps the indexing path byte-for-byte unchanged — no
     * understanding call runs. Gemini-only and <strong>fail-open</strong>: a
     * non-Gemini agent, a non-media document, or any understanding error simply
     * skips the enrichment and never blocks the document from being indexed. Unlike
     * the deterministic {@link #contentFitIndexingEnabled} signal, this <em>does</em>
     * spend an LLM call per media document, so it is off by default.
     *
     * @since 2026.3.4
     */
    @Column(name = "mediaUnderstandingIndexingEnabled", nullable = false)
    private boolean mediaUnderstandingIndexingEnabled = false;

    /**
     * T513 / §XXVIII.9 — per-site domain-specialized embedding model override
     * ({@code TurEmbeddingModel} id). When set, the public Semantic Navigation
     * hybrid-ranking path ({@link com.viglet.turing.sn.ranking.TurSNHybridRankingService})
     * indexes <em>and</em> queries this site's {@code sn_<siteId>} vector
     * collection through the chosen embedder — so a legal-docs site can pick
     * {@code voyage-law}, a code-search site {@code voyage-code}, a finance site
     * {@code voyage-finance} (or a Cohere domain model), each routed to the right
     * specialist while every other site stays on the platform default.
     *
     * <p>Plain id column (not a {@code @ManyToOne}) so the indexing/query path
     * resolves it via the embedding-model repository without dragging a LAZY
     * association across the request boundary, mirroring {@link #contentFitPersonaId}.
     * Null / blank falls back to the Global Settings default embedding model —
     * existing sites are byte-for-byte unchanged.
     *
     * <p><b>Operational note:</b> switching a site's embedding model changes the
     * vector space (and possibly the dimensionality), so the site's
     * {@code sn_<siteId>} collection must be cleared and re-indexed after a change;
     * index and query always use the same resolved model so they never mismatch
     * within one configuration.
     *
     * @since 2026.3.4
     */
    @Column(name = "embeddingModelId", length = 40)
    private String embeddingModelId;

    /**
     * T818 / §LIX.1 (Block BK) — the copilot's <strong>query-planning
     * strategy</strong>: how a natural-language catalog question is turned into a
     * structured DSL query.
     * {@link TurCopilotPlanningStrategy#DETERMINISTIC} is today's path (T811
     * ranking overlay + one LLM facet parse);
     * {@link TurCopilotPlanningStrategy#LLM_ASSISTED} is the T819 multi-pass
     * <em>parse → judge → refine</em> planner (i18n-native, N× LLM calls);
     * {@link TurCopilotPlanningStrategy#HYBRID} runs the deterministic fast-path
     * and escalates to the LLM passes only when retrieval comes back
     * empty/degenerate (T820).
     *
     * <p><b>Nullable on purpose:</b> {@code null} means "inherit the global
     * {@code turing.genai.copilot.planning.strategy} default" (itself
     * {@code DETERMINISTIC}), so a deployment can flip every site at once from
     * configuration while a single site can still pin its own strategy. Existing
     * sites read {@code null} and behave exactly as before.
     *
     * @since 2026.3.4
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "copilotPlanningStrategy", length = 32)
    private TurCopilotPlanningStrategy copilotPlanningStrategy;

    /**
     * T819 / §LIX.2 (Block BK) — the copilot's <strong>analysis depth</strong>:
     * how many LLM passes beyond the initial parse the planner may spend
     * ({@code 0} = parse only, {@code 1} = + judge, {@code 2} = + refine). Only
     * consulted when the resolved {@link #copilotPlanningStrategy} uses LLM passes
     * ({@code LLM_ASSISTED} / {@code HYBRID}).
     *
     * <p>{@code null} inherits the global
     * {@code turing.genai.copilot.planning.max-passes} property, so depth can be
     * tuned deployment-wide or pinned per site.
     *
     * @since 2026.3.4
     */
    @Column(name = "copilotPlanningMaxPasses")
    private Integer copilotPlanningMaxPasses;
}
