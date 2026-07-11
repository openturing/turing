/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import org.springframework.stereotype.Service;

/**
 * T687 / §XLII.1 — the {@link TurTranscriptionProviderType#NONE} backend:
 * transcription is explicitly disabled. Fail-soft — it never throws; callers get
 * a clear "not configured" result and can surface it cleanly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurNoneTranscriptionProvider implements TurTranscriptionProvider {

    @Override
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint) {
        return TurTranscriptionResult.fail("Transcription is disabled (backend = NONE).");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public TurTranscriptionProviderType getType() {
        return TurTranscriptionProviderType.NONE;
    }
}
