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
 * One synthesized theme in the insights report (Block AW / §XLVI.3, T722) — the
 * <em>by-theme</em> lens. Themes arrive ranked most-important first.
 * {@code prevalence} is the count of distinct personas whose verbatim quotes
 * support it (computed from {@code quotes}, not asserted by the model).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchThemeDto(
        String title,
        String summary,
        int prevalence,
        List<TurResearchQuoteDto> quotes) {
}
