/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research.dto;

import java.util.List;

/**
 * The <em>by-persona</em> lens of the insights report (Block AW / §XLVI.3, T722):
 * one participant with the quotes they contributed, each tagged with the theme it
 * landed under. Derived deterministically from the themes' quotes (no second LLM
 * call), so the two lenses are always consistent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchPersonaLensDto(
        String personaId,
        String personaName,
        List<Quote> quotes) {

    /** A verbatim quote from this persona, tagged with the theme it supports. */
    public record Quote(String theme, String quote) {
    }
}
