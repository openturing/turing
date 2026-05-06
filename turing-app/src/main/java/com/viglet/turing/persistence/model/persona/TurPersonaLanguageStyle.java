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
 * Coarse register of a {@link TurPersona}. The persona's free-text
 * {@code languageStyle} field can refine this further (e.g. "British
 * English, no contractions"); this enum is the dropdown the admin UI
 * picks from.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public enum TurPersonaLanguageStyle {
    NEUTRAL,
    DIRECT,
    NARRATIVE,
    PERSUASIVE,
    INSTRUCTIONAL
}
