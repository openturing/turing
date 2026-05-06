/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.util.List;

/**
 * Outcome of running a {@code TurPersonaToneValidator} over an LLM
 * response. Carries the (possibly rewritten) text plus the list of
 * forbidden-term hits, so callers can decide whether to log, surface a
 * warning, or persist the violation.
 *
 * <p>{@code passed == true} means no forbidden term was matched; in that
 * case {@code sanitizedText} is byte-for-byte the input.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurPersonaValidationResult(
        boolean passed,
        String sanitizedText,
        List<String> violations) {

    public static TurPersonaValidationResult passed(String text) {
        return new TurPersonaValidationResult(true, text, List.of());
    }
}
