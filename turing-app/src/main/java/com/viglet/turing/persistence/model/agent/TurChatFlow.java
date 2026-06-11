/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.turing.genai.flow.TurChatFlowRouterEvictionListener;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

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
 * Chat flow authored in the React Flow editor, scoped to a single AI agent.
 * The graph (nodes + edges) is serialized into {@link #definitionJson} so the
 * runtime advisor (Phase B) can deserialize it and walk the state machine.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Getter
@Setter
@Entity
@Table(name = "chat_flow")
@EntityListeners(TurChatFlowRouterEvictionListener.class)
public class TurChatFlow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Free-form admin description of the flow. Promoted from
     * {@code VARCHAR(500)} → {@code longtext} in v2026.2.7.7 — real-world
     * persona descriptions (e.g. Executive Education's Aula-Relâmpago professor at
     * ~580 chars) overflow the original cap. The trigger-description and
     * definition-json fields on this same entity already use {@code @Lob}
     * for the same reason; aligning the description here matches them.
     */
    @Lob
    @Column(columnDefinition = "longtext")
    private String description;

    @Lob
    @Column(name = "definitionJson", columnDefinition = "longtext")
    private String definitionJson;

    @Column(nullable = false)
    private int enabled = 1;

    /**
     * How the runtime engine enforces this flow. The column lets each flow
     * pick its own guard rail strategy as new methodologies (LLM-as-judge,
     * structured output, …) ship in later phases.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "guardrailMethod", nullable = false, length = 32)
    private TurChatFlowGuardrailMethod guardrailMethod = TurChatFlowGuardrailMethod.HEURISTIC;

    /**
     * T51 / §VII.4.e — capture-first inversion mode. Decides when a
     * slot-collecting {@code aiQuestion} node persists the user reply (before
     * vs. after the judge) and what gates the advance. Only honoured by the
     * {@code LLM_JUDGE} guardrail. Defaults to {@link
     * TurChatFlowCaptureMode#VALIDATE_THEN_CAPTURE} so existing flows keep
     * their pre-T51 behaviour.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "captureMode", nullable = false, length = 32)
    private TurChatFlowCaptureMode captureMode = TurChatFlowCaptureMode.VALIDATE_THEN_CAPTURE;

    /**
     * T53 / §VII.4.g — opt-in abandonment auto-escalation. When this text is
     * non-blank and the {@code LLM_JUDGE} guardrail detects abandonment, the
     * flow ends with THIS message (an offer to talk to a human consultant)
     * instead of the judge's plain goodbye, and writes a {@code
     * handoff_offered=abandon} tracking slot so the chat UI can surface a
     * handoff CTA and analytics can measure the abandon → handoff conversion.
     * Null/blank keeps the legacy behaviour: say goodbye and close the
     * session. Only honoured by the {@code LLM_JUDGE} guardrail (the other
     * methods have no abandonment signal).
     *
     * @since 2026.3.1
     */
    @Lob
    @Column(name = "abandonHandoffMessage", columnDefinition = "longtext")
    private String abandonHandoffMessage;

    /**
     * Natural-language description that the LLM router uses to decide whether
     * a user message should auto-trigger this flow. Empty/blank disables
     * auto-trigger — the flow can still be picked manually from the chat
     * dropdown.
     */
    @Lob
    @Column(name = "triggerDescription", columnDefinition = "longtext")
    private String triggerDescription;

    /** How often the router may auto-trigger this flow per conversation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "triggerMode", nullable = false, length = 16)
    private TurChatFlowTriggerMode triggerMode = TurChatFlowTriggerMode.ONCE;

    /**
     * T25 / §II.2.1 — natural language of {@link #triggerDescription}. Drives
     * the per-field analyzer routing in the procedural pre-route: PT flows
     * tokenize with {@code PortugueseAnalyzer}, EN with {@code EnglishAnalyzer},
     * {@code AUTO} (default) lets the router detect from description content.
     *
     * <p>Mismatched analyzer collapses morphological variants the wrong way —
     * EN Porter applied to PT mangles {@code "carreira" → "carreir"}, PT light
     * stem applied to EN leaves {@code "running"} as-is so it doesn't share a
     * stem with {@code "runs"}. Tagging the flow lets the overlap scorer find
     * the right matches without log inspection.
     *
     * @since 2026.3.1
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "triggerLanguage", nullable = false, length = 8)
    private TurChatFlowTriggerLanguage triggerLanguage = TurChatFlowTriggerLanguage.AUTO;

    /**
     * Optional experiment grouping key. Flows that share an
     * {@code experimentKey} are treated as variants of the same A/B
     * (or A/B/C/…) test: when the LLM router picks any one of them, the
     * engine deterministically reassigns to a single variant based on
     * {@code hash(conversationId + experimentKey)} weighted by
     * {@link #trafficWeight}. The assignment is sticky per conversation,
     * so the visitor stays on the same variant across re-triggers.
     *
     * <p>Blank/null disables A/B routing — the flow runs whenever the
     * router picks it, like every other flow.
     *
     * @since 2026.2.7
     */
    @Column(name = "experimentKey", length = 64)
    private String experimentKey;

    /**
     * Human-readable label for this variant ({@code "control"},
     * {@code "treatment_v2"}, {@code "hero_short"}, …). Used by the
     * analytics dashboard to group session metrics per variant — the
     * key alone identifies the experiment, the label identifies the arm.
     *
     * <p>Required when {@link #experimentKey} is set; ignored otherwise.
     *
     * @since 2026.2.7
     */
    @Column(name = "variantLabel", length = 64)
    private String variantLabel;

    /**
     * Relative traffic weight (0-100) for this variant within the
     * experiment. Two variants with weights {@code 50} and {@code 50}
     * split traffic evenly; weights {@code 90} and {@code 10} send 90%
     * of conversations to the first. Sum across variants does NOT have
     * to be 100 — the assignment math normalizes by total weight, so
     * {@code 1:1} and {@code 50:50} produce the same distribution.
     *
     * <p>{@code 0} or {@code null} means "exclude from traffic" — useful
     * to keep a variant declared (so completed sessions can be analyzed)
     * while pausing fresh assignments to it.
     *
     * @since 2026.2.7
     */
    @Column(name = "trafficWeight")
    private Integer trafficWeight;

    /**
     * Optional schedule window opening. When set, the variant is excluded
     * from fresh A/B assignment until {@code now() >= experimentStartsAt}
     * — so a flow author can stage a treatment variant ahead of time and
     * have it auto-go-live without an admin click. Conversations already
     * assigned to this variant continue uninterrupted (assignment is
     * sticky); only first-touch traffic is gated.
     *
     * <p>{@code null} means "live now" — no scheduling constraint.
     *
     * @since 2026.2.7
     */
    @Column(name = "experimentStartsAt")
    private Instant experimentStartsAt;

    /**
     * Optional schedule window closing. When set, the variant is excluded
     * from fresh A/B assignment after {@code now() > experimentEndsAt} —
     * letting a campaign-specific variant auto-retire at the end of a
     * promo window without leaving lingering traffic. Same stickiness
     * applies: in-flight conversations finish on their assigned variant.
     *
     * <p>If every variant in an experiment has expired, the engine falls
     * back to the LLM-router-picked flow as if no A/B were configured.
     *
     * <p>{@code null} means "no end date" — the variant stays in
     * rotation until disabled manually.
     *
     * @since 2026.2.7
     */
    @Column(name = "experimentEndsAt")
    private Instant experimentEndsAt;

    /**
     * T70 / §VII.8.a — bandit mode for A/B assignment. When at least one
     * variant in the experimentKey has this set to {@code TRUE}, the
     * engine ignores {@code trafficWeight} and selects the variant via
     * Thompson sampling on the observed conversion data (Beta-Bernoulli
     * posterior, success metric configured per experiment).
     *
     * <p>The toggle is per-flow rather than per-experimentKey so a single
     * arm can opt into bandit assignment incrementally (e.g. a champion-
     * challenger setup where the champion stays fixed-weight while the
     * challenger gets bandit traffic until it earns a fixed slot).
     *
     * <p>{@code null} / {@code FALSE} → legacy deterministic
     * weighted-hash assignment (current production behaviour).
     *
     * @since 2026.3.1
     */
    @Column(name = "banditEnabled")
    private Boolean banditEnabled;

    /**
     * T71 / §VII.8.b — champion-challenger auto-promotion opt-in. When any
     * variant under the same {@code experimentKey} has this set to
     * {@code TRUE}, the daily
     * {@link com.viglet.turing.service.chatanalytics.TurChampionChallengerPromotionJob}
     * evaluates the experiment's statistical significance and, once a winner
     * is declared, auto-promotes the winner to {@code trafficWeight=100} and
     * archives the losing arms ({@code trafficWeight=0} +
     * {@code experimentEndsAt=now}, closing their assignment window). Bandit
     * mode is also cleared on every arm once a champion is fixed.
     *
     * <p>Opt-in per the project convention that behaviour changes ship as a
     * choosable per-entity flag (default = legacy): {@code null} / {@code FALSE}
     * leaves the experiment under manual operator control — significance is
     * still computable on demand, but no traffic is reassigned automatically.
     * The manual {@code POST /experiment/{key}/promote} endpoint ignores this
     * flag (an explicit operator click always promotes).
     *
     * @since 2026.3.1
     */
    @Column(name = "autoPromote")
    private Boolean autoPromote;

    /**
     * T93 / §VII.11.c — cross-flow slot inheritance. JSON object whose keys
     * are slot names this (receiving) flow expects to inherit from earlier
     * flows on the same conversation, and whose values are the source-slot
     * names to copy from. Use the same name on both sides for a straight
     * pass-through (e.g. {@code {"name": "name"}}); use different names to
     * rename (e.g. {@code {"position": "cargo_atual"}} brings the B2C flow's
     * {@code cargo_atual} into the B2B flow as {@code position}).
     *
     * <p>Applied by both the manual pin
     * ({@link com.viglet.turing.genai.flow.TurChatFlowEngineService#pinFlowForConversation})
     * and the auto-router transition into a fresh flow. Null/blank → no
     * inheritance, matches the pre-T93 behaviour where every flow started
     * with an empty slot map.
     *
     * <p>{@link tools.jackson.databind.ObjectMapper} parses this at apply
     * time, not on the JPA load — so a malformed JSON value is logged but
     * does not crash the flow.
     *
     * @since 2026.3.1
     */
    @Lob
    @Column(name = "slotInheritanceJson", columnDefinition = "longtext")
    private String slotInheritanceJson;

    /**
     * T96 / §VII.11.f — stable id of the recipe this flow was installed from
     * (e.g. {@code lead-capture-b2c}). {@code null} when the flow was
     * hand-authored or imported from a raw JSON export. Drives the
     * "Installed from recipe" badge in the editor and powers a future
     * "upgrade available" UX when the bundled recipe ships a v2.
     *
     * @since 2026.3.1
     */
    @Column(name = "installedFromRecipe", length = 64)
    private String installedFromRecipe;

    /**
     * T96 / §VII.11.f — semver of the source recipe at install time. Set
     * together with {@link #installedFromRecipe}; null otherwise.
     *
     * @since 2026.3.1
     */
    @Column(name = "installedRecipeVersion", length = 32)
    private String installedRecipeVersion;

    /**
     * Owning AI agent. The client always knows the agent id from the URL, so
     * the back-reference is hidden from JSON to keep the payload small and
     * avoid serializing the full agent graph.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    @JsonIgnore
    private TurAIAgent turAIAgent;
}
