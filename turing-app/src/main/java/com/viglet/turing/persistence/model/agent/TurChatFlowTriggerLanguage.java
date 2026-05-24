/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

/**
 * T25 / §II.2.1 — natural language of a chat flow's {@code triggerDescription}.
 *
 * <p>Drives the per-field analyzer chain in
 * {@code TurChatFlowEngineService.tryProceduralRoute}: a flow tagged
 * {@link #EN} tokenizes its description (and the user message, when matched
 * against this flow) through {@code EnglishAnalyzer} so Porter stems and
 * English stopwords work properly. {@link #PT} routes through
 * {@code PortugueseAnalyzer} (the platform default). {@link #AUTO} lets the
 * runtime detect the language from the description content at routing time —
 * the right pick for tenants that don't want to manually tag every flow but
 * tolerate the (negligible) per-route detection cost.
 *
 * <p>Procedural router scoring quality depends on this: a mismatched
 * analyzer collapses morphological variants the wrong way (PT light stemmer
 * applied to EN reduces {@code "running"} → {@code "running"} unchanged,
 * losing the {@code "runs"}/{@code "ran"} overlap; EN Porter applied to PT
 * mangles {@code "carreira"} → {@code "carreir"} and {@code "carro"} →
 * {@code "carro"} so they don't share a stem with their PT variants).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurChatFlowTriggerLanguage {

    /** Auto-detect language from the trigger description at routing time. Default. */
    AUTO,

    /** Portuguese. Analyzer chain: {@code PortugueseAnalyzer} (light stemming + PT stopwords). */
    PT,

    /** English. Analyzer chain: {@code EnglishAnalyzer} (Porter stemming + EN stopwords). */
    EN
}
