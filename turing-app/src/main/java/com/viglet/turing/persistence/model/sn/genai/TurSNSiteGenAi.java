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
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;
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
    @TurAssignableUuidGenerator
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
}
