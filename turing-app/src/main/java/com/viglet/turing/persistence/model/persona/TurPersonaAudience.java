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

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

/**
 * The <em>audience facet</em> of a {@link TurPersona} — the missing half that
 * models the <strong>reader</strong> rather than the speaker. Populated only
 * when {@link TurPersona#personaKind} is {@code AUDIENCE} or {@code BOTH};
 * read only by the content-fit evaluation path (Block AA, §XXVI). The prompt
 * composer never touches it, so default {@code SPEAKER} personas keep these
 * columns dead-null and behave exactly as before.
 *
 * <p>Modelled as a JPA {@link Embeddable} value object (not a fork of
 * {@code TurPersona}, not a loose pile of nullable columns) — see §XXVI.1 for
 * the rationale and the escape hatch to extract it into its own entity later.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Embeddable
public class TurPersonaAudience implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Education / reading-grade band — the complexity ceiling for the reader. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reading_level", length = 16)
    private TurPersonaReadingLevel readingLevel;

    /** Domain familiarity — drives jargon / acronym tolerance. */
    @Enumerated(EnumType.STRING)
    @Column(name = "domain_expertise", length = 16)
    private TurPersonaDomainExpertise domainExpertise;

    /**
     * Free text or a term list describing the vocabulary budget the reader can
     * follow. The deterministic readability scorer (T466) measures the
     * complex-word ratio against this.
     */
    @Lob
    @Column(name = "vocabulary_ceiling")
    private String vocabularyCeiling;

    /**
     * Comprehension constraints in free text — e.g. "second-language reader",
     * "skims rather than reads", "needs concrete examples".
     */
    @Lob
    @Column(name = "comprehension_notes")
    private String comprehensionNotes;

    /**
     * Accessibility constraints in free text — e.g. "dyslexia-friendly",
     * "screen reader", "high-contrast / short paragraphs".
     */
    @Lob
    @Column(name = "accessibility_notes")
    private String accessibilityNotes;

    /**
     * Primary reading language as an ISO-639 code ({@code pt}, {@code en},
     * {@code es}) selecting the readability metric's language profile.
     */
    @Column(name = "primary_language", length = 8)
    private String primaryLanguage;
}
