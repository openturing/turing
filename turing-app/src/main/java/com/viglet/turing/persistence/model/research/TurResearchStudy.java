/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.research;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A Synthetic User Research study (Block AW / §XLVI.2, T719) — the new
 * first-class research aggregate. It reframes persona work from "act on one
 * persona at a time" into "interview a whole cohort as a method": a study
 * carries a research {@code goal} + {@code hypothesis}, an ordered
 * <em>audience</em> roster of existing personas (through
 * {@link TurResearchStudyPersona}, picked with the shared {@code PersonaSelectGrid}),
 * and an interview {@link TurResearchProtocol protocol}; it owns one
 * {@link TurResearchInterview per-persona transcript}.
 *
 * <p>Deliberately mirrors the Persona Match project (Block AT) aggregate shape —
 * {@code @TenantId} discriminator, {@code llmInstanceId} override, {@code schedule}
 * (added by the Phase 4 Continuous-Insight task), and a {@code lastRunAt} stamp —
 * so the studio (T730) can reuse the Match/Dialogue project-mosaic pattern. This
 * is an admin-CRUD aggregate, so it uses plain JPA repositories (no domain
 * records/ports, per the Block AF stop-criteria).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_research_study")
public class TurResearchStudy implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator. Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** The research goal that drives the DYNAMIC_SCRIPT interviewer sub-loop. */
    @Lob
    @Column(name = "goal")
    private String goal;

    /** The hypothesis the study is probing (framing for the interviewer). */
    @Lob
    @Column(name = "hypothesis")
    private String hypothesis;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 16)
    private TurResearchProtocol protocol = TurResearchProtocol.DYNAMIC_SCRIPT;

    /** CONCEPT_TEST only — the proposed concept/message participants react to. */
    @Lob
    @Column(name = "concept_text")
    private String conceptText;

    /** CUSTOM_SCRIPT only — a JSON array of the fixed questions asked verbatim. */
    @Lob
    @Column(name = "questions_json")
    private String questionsJson;

    /** DYNAMIC_SCRIPT / CONCEPT_TEST — the adaptive follow-up question cap. */
    @Column(name = "max_questions", nullable = false)
    private int maxQuestions = 6;

    /**
     * The study-wide LLM instance override (null = default). Per-stage lanes
     * ({@link #interviewLlmInstanceId} / {@link #synthesisLlmInstanceId}) take
     * precedence over this; this in turn takes precedence over the platform default.
     */
    @Column(name = "llmInstanceId", length = 40)
    private String llmInstanceId;

    /**
     * T728 / §XLVI.4 — "Big Shuffle" per-stage model lane: the LLM instance that
     * drives the <em>interview</em> stage (interviewer sub-loop + answers), so a
     * fast/cheap model can conduct interviews independent of synthesis. Null falls
     * back to {@link #llmInstanceId} then the default LLM.
     */
    @Column(name = "interview_llm_instance_id", length = 40)
    private String interviewLlmInstanceId;

    /**
     * T728 / §XLVI.4 — per-stage model lane for the <em>synthesis</em> stage (the
     * T722 insights report), so a stronger model can synthesize without its bias
     * coloring the interviews. Null falls back to {@link #llmInstanceId} then the
     * default LLM. (Saturation scoring is deterministic / LLM-free, so it has no
     * lane.)
     */
    @Column(name = "synthesis_llm_instance_id", length = 40)
    private String synthesisLlmInstanceId;

    /**
     * T726 / §XLVI.4 — the study <em>target</em>. When set, the study interviews
     * this live {@link com.viglet.turing.persistence.model.agent.TurAIAgent} (its
     * real system prompt, tools and RAG) instead of a bare persona-fused LLM: the
     * roster persona plays the synthetic <em>user</em> testing/red-teaming the
     * deployed agent, which plays the assistant. Null (the default) keeps the
     * legacy behaviour — the persona is the interviewee answered by a bare LLM.
     */
    @Column(name = "target_agent_id", length = 40)
    private String targetAgentId;

    /**
     * T729 / §XLVI.4 — Continuous Insight cadence. MANUAL (default) runs only on an
     * explicit "run now"; DAILY/WEEKLY are picked up by the scheduled re-run job.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule", nullable = false, length = 16)
    private TurResearchSchedule schedule = TurResearchSchedule.MANUAL;

    /** When the last cohort interview run completed (null until first run). */
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "creationDate")
    private Instant creationDate;

    @Column(name = "modificationDate")
    private Instant modificationDate;
}
