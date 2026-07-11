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

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * T690 / §XLII.4 — the {@link TurTranscriptionProviderType#OPENAI_COMPATIBLE}
 * backend: the recommended local path. Points the shared REST client at a
 * self-hosted server speaking the OpenAI {@code /audio/transcriptions} contract
 * ({@code faster-whisper-server}, {@code whisper.cpp} server, vLLM-whisper) via a
 * <b>dedicated</b> endpoint/model/key from the transcription config — decoupled
 * from the default {@code TurLLMInstance} (unlike {@link TurOpenAiTranscriptionProvider}).
 * The API key is optional: most self-hosted servers require none, so a blank key
 * simply omits the {@code Authorization} header. Scales horizontally, is
 * GPU-capable, and keeps the same code path as the cloud provider — only the URL
 * changes. Ship a {@code viglet/cloud} sidecar recipe (see
 * {@code docs/references/transcription-local-backend.md}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurOpenAiCompatibleTranscriptionProvider implements TurTranscriptionProvider {

    private final TurTranscriptionConfigResolver configResolver;
    private final TurOpenAiTranscriptionClient client;

    public TurOpenAiCompatibleTranscriptionProvider(TurTranscriptionConfigResolver configResolver,
            TurOpenAiTranscriptionClient client) {
        this.configResolver = configResolver;
        this.client = client;
    }

    @Override
    public TurTranscriptionProviderType getType() {
        return TurTranscriptionProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.isNotBlank(configResolver.resolve().endpoint());
    }

    @Override
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint) {
        TurTranscriptionConfig config = configResolver.resolve();
        if (StringUtils.isBlank(config.endpoint())) {
            return TurTranscriptionResult.fail(
                    "OPENAI_COMPATIBLE transcription requires a dedicated endpoint "
                            + "(set turing.transcription.endpoint or the Transcription endpoint in Global Settings).");
        }
        return client.transcribe(config.endpoint(), config.apiKey(), config.model(),
                config.maxUploadBytes(), audio, mimeType, languageHint);
    }
}
