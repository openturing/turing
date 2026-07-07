/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.capture;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * T618 / §XXXIV.6 — tolerant parsing of a storage object's {@code lastModified}
 * string into epoch millis.
 *
 * <p>The {@link com.viglet.turing.service.storage.TurStorageService} backends
 * (MinIO / filesystem) report {@code lastModified} in different formats. Rather
 * than couple the capture store to any one backend's format, this helper tries
 * the common representations and returns {@code 0} when none parse — callers
 * treat {@code 0} as "age unknown" (the picker shows the turn index only; the
 * retention sweep never deletes an undated entry).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurPromptCaptureTimes {

    private TurPromptCaptureTimes() {
    }

    static long toEpochMillis(String lastModified) {
        if (lastModified == null || lastModified.isBlank()) {
            return 0L;
        }
        String value = lastModified.trim();
        // Epoch millis (or seconds) as a plain number.
        try {
            long number = Long.parseLong(value);
            // Heuristic: < ~ year 2001 in millis means it was seconds.
            return number < 1_000_000_000_000L ? number * 1000L : number;
        } catch (NumberFormatException ignored) {
            // fall through to date formats
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(value).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
