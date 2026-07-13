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

/**
 * T718 / §XLVI.1 — where a {@link TurPersona}'s answers are grounded. When a
 * persona is used as a research participant (or in persona chat), binding it to a
 * knowledge source makes its answers come from proprietary content retrieved
 * through the existing RAG stack rather than the base model's priors.
 *
 * <ul>
 *   <li>{@link #NONE} — the default: no grounding, the persona answers from the
 *       model's own knowledge. Every existing persona is byte-for-byte
 *       unchanged.</li>
 *   <li>{@link #SN_SITE} — ground on a Semantic Navigation site's indexed corpus;
 *       relevant chunks are retrieved per turn from that site's vector store.</li>
 *   <li>{@link #NOTEBOOK} — ground on the persona's own Block AA notebook source
 *       set ({@code TurPersonaSource}: uploaded assets / URLs / indexed docs).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaGroundingSource {
    NONE,
    SN_SITE,
    NOTEBOOK
}
