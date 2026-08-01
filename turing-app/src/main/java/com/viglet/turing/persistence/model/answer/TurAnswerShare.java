/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.answer;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T844 / §LXII.13 — a persisted <b>answer permalink</b>: an immutable snapshot of
 * one answer (the query, the active facet filter state, the source snapshot, the
 * synthesized answer text and the instant it was produced) addressable at a stable
 * URL that re-renders it read-only. Turns every good answer into a linkable artifact
 * and, on public deployments, indexable content (pairs with Block O / GEO).
 *
 * <p>The snapshot is deliberately stored verbatim — the shared answer must render
 * exactly as it was, even after the underlying index has drifted (T840 tells you
 * <em>that</em> it drifted; the permalink preserves what was said). Sources,
 * citations and filters are kept as opaque JSON the client serialized.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_answer_share")
public class TurAnswerShare implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    /** The SN site the answer was produced over. */
    @Column(name = "site_name", nullable = false, length = 255)
    private String siteName;

    /** The user's question. */
    @Column(name = "query", nullable = false, length = 2000)
    private String query;

    /** The active facet filter state (JSON {@code field -> values}), when steered (T838). */
    @Column(name = "filters_json")
    private String filtersJson;

    /** The synthesized answer text (markdown). */
    @Column(name = "answer_text")
    private String answerText;

    /** The source snapshot (JSON array of the answer's numbered sources). */
    @Column(name = "sources_json")
    private String sourcesJson;

    /** The citation snapshot (JSON array of the answer's footnote citations). */
    @Column(name = "citations_json")
    private String citationsJson;

    /** When the answer was produced (drift is measured from here, T840). */
    @Column(name = "answered_at")
    private Instant answeredAt;

    /** When this permalink was minted. */
    @Column(name = "creation_date")
    private LocalDateTime creationDate;
}
