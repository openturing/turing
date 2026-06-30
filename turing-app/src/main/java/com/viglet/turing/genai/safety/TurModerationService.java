/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.models.moderations.Moderation;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T182 / §X.14.b — OpenAI {@code omni-moderation-latest} pre-filter. Runs a
 * user query (and, over a configurable size, a crawler-discovered document
 * chunk) through OpenAI's multimodal moderation endpoint <strong>before</strong>
 * it reaches the LLM or the index. Moderation is free at the API tier, so this
 * adds a safety/compliance gate without metered cost.
 *
 * <p>Strictly opt-in and fully fail-open. When
 * {@code turing.safety.moderation.enabled=false} (default), no OpenAI instance
 * is configured, or the moderation call errors, {@link #moderate(String)}
 * returns a {@link Verdict#clean() clean} verdict so the caller proceeds exactly
 * as before — moderation must never take the platform down or block legitimate
 * traffic on an outage.
 *
 * <p>The endpoint requires an OpenAI key. The service resolves an OpenAI client
 * from the default LLM when that instance is OpenAI, otherwise from the first
 * enabled OpenAI instance it finds — independent of which vendor actually
 * answers the chat turn (a tenant on Claude can still moderate via its OpenAI
 * key).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurModerationService {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurNativeProviderClient nativeClient;
    private final boolean enabled;
    private final String model;
    private final int chunkMinChars;

    public TurModerationService(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurGenAiLlmProviderFactory providerFactory,
            TurNativeProviderClient nativeClient,
            @Value("${turing.safety.moderation.enabled:false}") boolean enabled,
            @Value("${turing.safety.moderation.model:omni-moderation-latest}") String model,
            @Value("${turing.safety.moderation.chunk-min-chars:512}") int chunkMinChars) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.providerFactory = providerFactory;
        this.nativeClient = nativeClient;
        this.enabled = enabled;
        this.model = StringUtils.hasText(model) ? model : "omni-moderation-latest";
        this.chunkMinChars = chunkMinChars;
    }

    /** The moderation outcome: whether the text was flagged and under which categories. */
    public record Verdict(boolean flagged, List<String> categories) {
        public static Verdict clean() {
            return new Verdict(false, List.of());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Moderate arbitrary text (a user query or a document chunk). Fail-open:
     * disabled / blank / no OpenAI client / API error → {@link Verdict#clean()}.
     */
    public Verdict moderate(String text) {
        if (!enabled || !StringUtils.hasText(text)) {
            return Verdict.clean();
        }
        return moderateAlways(text);
    }

    /**
     * T516 / §XXVIII.12 — moderate {@code text} <strong>regardless</strong> of
     * the T182 {@code turing.safety.moderation.enabled} switch, used by the
     * answer-grounding guardrail whose own {@code turing.safety.guardrail.*}
     * switch decides when it runs. Still fully fail-open: blank text / no OpenAI
     * client / API error → {@link Verdict#clean()}.
     */
    public Verdict moderateAlways(String text) {
        if (!StringUtils.hasText(text)) {
            return Verdict.clean();
        }
        Optional<OpenAIClient> client = resolveOpenAiClient();
        if (client.isEmpty()) {
            log.debug("[Moderation] enabled but no OpenAI instance available — skipping (fail-open)");
            return Verdict.clean();
        }
        try {
            ModerationCreateResponse response = client.get().moderations().create(
                    ModerationCreateParams.builder().input(text).model(model).build());
            if (response.results().isEmpty()) {
                return Verdict.clean();
            }
            Moderation result = response.results().get(0);
            if (!result.flagged()) {
                return Verdict.clean();
            }
            return new Verdict(true, flaggedCategories(result.categories()));
        } catch (RuntimeException e) {
            log.warn("[Moderation] moderation call failed — failing open: {}", e.getMessage());
            return Verdict.clean();
        }
    }

    /**
     * Whether a document chunk is large enough to be worth moderating. The
     * crawler emits many tiny fragments (nav labels, single words); moderating
     * each would be wasteful, so only chunks at or over
     * {@code turing.safety.moderation.chunk-min-chars} are checked.
     */
    public boolean shouldModerateChunk(String chunk) {
        return enabled && chunk != null && chunk.length() >= chunkMinChars;
    }

    /**
     * T182 / §X.14.b — filter a chunk list, dropping any large chunk that
     * moderation flags before it reaches the index. Returns the input unchanged
     * when moderation is off; fail-open per {@link #moderate(String)} so an
     * outage never silently empties an ingestion batch (a failed call yields a
     * clean verdict and the chunk is kept). Generic over the chunk's text
     * accessor so callers can pass Spring AI {@code Document}s without this
     * service depending on that type's shape beyond reading text.
     */
    public <T> List<T> filterModeratedChunks(List<T> chunks,
            java.util.function.Function<T, String> textOf) {
        if (!enabled || chunks == null || chunks.isEmpty()) {
            return chunks == null ? List.of() : chunks;
        }
        List<T> kept = new ArrayList<>(chunks.size());
        int dropped = 0;
        for (T chunk : chunks) {
            String text = textOf.apply(chunk);
            if (shouldModerateChunk(text) && moderate(text).flagged()) {
                dropped++;
                continue;
            }
            kept.add(chunk);
        }
        if (dropped > 0) {
            log.info("[Moderation] dropped {} of {} indexed chunk(s) flagged by moderation",
                    dropped, chunks.size());
        }
        return kept;
    }

    private List<String> flaggedCategories(Moderation.Categories categories) {
        List<String> flagged = new ArrayList<>();
        if (categories.harassment()) {
            flagged.add("harassment");
        }
        if (categories.hate()) {
            flagged.add("hate");
        }
        if (categories.illicit().orElse(false)) {
            flagged.add("illicit");
        }
        if (categories.selfHarm()) {
            flagged.add("self-harm");
        }
        if (categories.sexual()) {
            flagged.add("sexual");
        }
        if (categories.sexualMinors()) {
            flagged.add("sexual/minors");
        }
        if (categories.violence()) {
            flagged.add("violence");
        }
        return flagged;
    }

    /**
     * An OpenAI client for moderation: the default LLM when it is an OpenAI
     * instance, otherwise the first enabled OpenAI instance. Empty when no
     * OpenAI instance exists or none yields a usable client.
     */
    private Optional<OpenAIClient> resolveOpenAiClient() {
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (StringUtils.hasText(defaultLlmId)) {
            Optional<OpenAIClient> fromDefault = llmInstanceRepository.findById(defaultLlmId)
                    .filter(instance -> instance.getEnabled() == 1)
                    .filter(this::isOpenAi)
                    .flatMap(nativeClient::openAi);
            if (fromDefault.isPresent()) {
                return fromDefault;
            }
        }
        return llmInstanceRepository.findAll().stream()
                .filter(instance -> instance.getEnabled() == 1)
                .filter(this::isOpenAi)
                .flatMap(instance -> nativeClient.openAi(instance).stream())
                .findFirst();
    }

    private boolean isOpenAi(TurLLMInstance instance) {
        try {
            return "openai".equals(
                    providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
