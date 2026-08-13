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

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The audience-facet fields a Persona Match adjustment is allowed to write
 * (T831 / §LXI.7), and the only ones — {@code accessibilityNotes} and
 * {@code primaryLanguage} are deliberately out of reach: neither is something a
 * fit verdict has evidence about.
 * <p>
 * Each constant carries its own read and write accessor, so the apply path
 * touches one field at a time instead of replacing the whole
 * {@link TurPersonaAudience} embeddable — a wholesale copy would clobber the
 * fields the operator did <em>not</em> confirm, which is exactly what
 * field-by-field confirmation exists to prevent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaAudienceField {

    READING_LEVEL(true,
            a -> a.getReadingLevel() == null ? null : a.getReadingLevel().name(),
            (a, v) -> a.setReadingLevel(v == null ? null
                    : TurPersonaReadingLevel.valueOf(v))),

    DOMAIN_EXPERTISE(true,
            a -> a.getDomainExpertise() == null ? null : a.getDomainExpertise().name(),
            (a, v) -> a.setDomainExpertise(v == null ? null
                    : TurPersonaDomainExpertise.valueOf(v))),

    VOCABULARY_CEILING(false,
            TurPersonaAudience::getVocabularyCeiling,
            TurPersonaAudience::setVocabularyCeiling),

    COMPREHENSION_NOTES(false,
            TurPersonaAudience::getComprehensionNotes,
            TurPersonaAudience::setComprehensionNotes);

    private final boolean enumerated;
    private final Function<TurPersonaAudience, String> reader;
    private final BiConsumer<TurPersonaAudience, String> writer;

    TurPersonaAudienceField(boolean enumerated, Function<TurPersonaAudience, String> reader,
            BiConsumer<TurPersonaAudience, String> writer) {
        this.enumerated = enumerated;
        this.reader = reader;
        this.writer = writer;
    }

    /** Whether the field only accepts one of a closed set of values. */
    public boolean isEnumerated() {
        return enumerated;
    }

    /** The field's current value as a string, or {@code null} when unset. */
    public String read(TurPersonaAudience audience) {
        return audience == null ? null : reader.apply(audience);
    }

    /**
     * Writes the value, after {@link #accepts(String)} has cleared it. Callers
     * must validate first: an enumerated field throws on an unknown constant.
     */
    public void write(TurPersonaAudience audience, String value) {
        writer.accept(audience, value);
    }

    /**
     * Whether this field would take the value. Always true for a free-text
     * field; for an enumerated one, only an exact constant name.
     */
    public boolean accepts(String value) {
        if (!enumerated) {
            return true;
        }
        if (value == null) {
            return true;
        }
        return switch (this) {
            case READING_LEVEL -> isConstant(TurPersonaReadingLevel.class, value);
            case DOMAIN_EXPERTISE -> isConstant(TurPersonaDomainExpertise.class, value);
            default -> true;
        };
    }

    /** The closed set of accepted values, empty for a free-text field. */
    public String[] allowedValues() {
        return switch (this) {
            case READING_LEVEL -> names(TurPersonaReadingLevel.values());
            case DOMAIN_EXPERTISE -> names(TurPersonaDomainExpertise.values());
            default -> new String[0];
        };
    }

    /**
     * The constant this name refers to, or empty for anything unknown. Spelling
     * is compared with every separator removed, because the model is as likely
     * to answer {@code readingLevel} or {@code reading-level} as
     * {@code READING_LEVEL}, and none of those is a different field.
     */
    public static Optional<TurPersonaAudienceField> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = squash(raw);
        for (TurPersonaAudienceField field : values()) {
            if (squash(field.name()).equals(normalized)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    private static String squash(String raw) {
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private static <E extends Enum<E>> boolean isConstant(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String[] names(Enum<?>[] constants) {
        String[] out = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            out[i] = constants[i].name();
        }
        return out;
    }
}
