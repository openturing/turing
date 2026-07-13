/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.genai.persona.TurPersonaStaticPromptEvictionListener;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A reusable voice profile that can be attached to a
 * {@link com.viglet.turing.persistence.model.agent.TurAIAgent}. The persona
 * fuses into the system prompt at chat time: base instruction, style
 * guidelines (tone / verbosity / language style), required + forbidden
 * vocabulary, optional brand context pulled from an MCP server, and
 * few-shot Q/A examples retrieved by similarity from a dedicated vector
 * store.
 *
 * <p>Forbidden terms are also enforced post-LLM by
 * {@code TurPersonaToneValidator}: matches are masked before the response
 * leaves the chat executor.
 *
 * <p>Both {@code mandatoryTerms} and {@code forbiddenTerms} are pipe
 * (<code>|</code>)-separated rather than comma-separated so individual
 * terms can themselves contain commas.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona")
@EntityListeners(TurPersonaStaticPromptEvictionListener.class)
public class TurPersona implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Block AA / §XXVI.1 — what this persona is usable <em>for</em>. Default
     * {@link TurPersonaKind#SPEAKER} keeps every existing voice persona, agent
     * attachment, and prompt-composition path byte-for-byte unchanged. Only
     * {@code AUDIENCE}/{@code BOTH} personas carry an {@link #audience} facet.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "persona_kind", length = 16, nullable = false)
    private TurPersonaKind personaKind = TurPersonaKind.SPEAKER;

    /**
     * The reader/audience facet — populated only when {@link #personaKind} is
     * {@code AUDIENCE} or {@code BOTH}; read only by the content-fit evaluator.
     */
    @Embedded
    private TurPersonaAudience audience;

    @Lob
    @Column(name = "system_instruction")
    private String systemInstruction;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TurPersonaTone tone;

    @Column(nullable = false)
    private int verbosity = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "language_style", length = 16)
    private TurPersonaLanguageStyle languageStyle;

    @Lob
    @Column(name = "mandatory_terms")
    private String mandatoryTerms;

    @Lob
    @Column(name = "forbidden_terms")
    private String forbiddenTerms;

    @Column(nullable = false)
    private int enabled = 1;

    /**
     * T605 — opt-in style→model calibration. When {@code 1}, the persona's style
     * guidelines calibrate the resolved sampling params for the turn
     * ({@link #verbosity} → {@code maxTokens}, {@link #tone} → {@code temperature},
     * see {@code TurPersonaModelCalibration}). Default {@code 0} keeps the LLM
     * instance's own params, so existing personas are byte-for-byte unchanged.
     */
    @Column(name = "calibrate_model_params", nullable = false)
    private int calibrateModelParams = 0;

    /**
     * T717 / §XLVI.1 — opt-in Big Five (OCEAN) personality facet. Each trait is a
     * 0–100 score; {@code null} means "unset" and renders nothing, so an existing
     * persona is byte-for-byte unchanged. When set, the traits (a) render into the
     * system prompt as behavioral guidance (see {@code TurPersonaStaticPromptCache})
     * and (b) nudge the calibrated sampling temperature when
     * {@link #calibrateModelParams} is on (see {@code TurPersonaModelCalibration}),
     * so a synthetic-research cohort answers <em>differently</em> instead of
     * collapsing to one voice.
     */
    @Column
    private Integer openness;

    @Column
    private Integer conscientiousness;

    @Column
    private Integer extraversion;

    @Column
    private Integer agreeableness;

    @Column
    private Integer neuroticism;

    /**
     * T718 / §XLVI.1 — opt-in knowledge binding. When not {@link
     * TurPersonaGroundingSource#NONE}, the persona's answers are grounded in
     * retrieved proprietary content (an SN site's corpus, or this persona's Block
     * AA notebook) instead of the model's priors. Default {@code NONE} keeps every
     * existing persona byte-for-byte unchanged.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grounding_source", length = 16, nullable = false)
    private TurPersonaGroundingSource groundingSource = TurPersonaGroundingSource.NONE;

    /** The bound Semantic Navigation site name — used only when {@link #groundingSource} is {@code SN_SITE}. */
    @Column(name = "grounding_sn_site", length = 255)
    private String groundingSnSite;

    @ManyToOne
    @JoinColumn(name = "few_shot_store_id")
    private TurStoreInstance fewShotStore;

    @ManyToOne
    @JoinColumn(name = "brand_context_mcp_server_id")
    private TurMcpServer brandContextMcpServer;

    /**
     * True when this persona may be used as an agent's voice. Null-kind is
     * treated as the legacy {@code SPEAKER} contract so pre-Block-AA rows stay
     * usable. The prompt composer and agent attachment gate on this.
     */
    public boolean isUsableAsSpeaker() {
        return personaKind == null
                || personaKind == TurPersonaKind.SPEAKER
                || personaKind == TurPersonaKind.BOTH;
    }

    /**
     * True when this persona carries an audience facet that the content-fit
     * evaluator may read.
     */
    public boolean isUsableAsAudience() {
        return personaKind == TurPersonaKind.AUDIENCE
                || personaKind == TurPersonaKind.BOTH;
    }
}
