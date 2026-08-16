/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.vigletai.TurVigletInstallIdentityService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurVigletAiProperty;

/**
 * T956 / §XIV.10.1 — <b>Viglet AI</b>, the managed LLM endpoint behind the
 * onboarding credit (Block J.10).
 *
 * <p>The endpoint is already reachable through {@link TurOpenAiCompatibleLlmProvider}
 * — by an operator who knows the URL, picks the right vendor row and types a
 * model name. This thin subclass removes all three choices: the {@code baseUrl}
 * is pinned to {@link TurVigletAiProperty#getBaseUrl()} and the model is pinned
 * to the opaque alias {@link TurVigletAiProperty#getModelAlias()} (default
 * {@code viglet-fast}), for chat and for embeddings alike. Whatever an instance
 * carries in {@code url}, {@code modelName} or its {@code baseUrl}/{@code model}
 * provider options is ignored.
 *
 * <p><b>The alias is a decision, not plumbing.</b> Naming the vendor model in
 * the wire contract would fix Turing's cost to one vendor's price sheet and
 * invite the per-token comparison this credit is designed not to have; an alias
 * lets the managed service move to whatever is cheapest per useful token without
 * touching a single install. {@link #listModels} therefore returns exactly that
 * one id — the whole catalogue, so there is nothing to shop — without a network
 * call.
 *
 * <p><b>Capabilities follow the alias, not the model behind it:</b> chat and
 * embedding only. No {@code TurNativeCapability} declares the {@code viglet}
 * plugin type, so the per-instance native matrix (T132) is empty by construction
 * — no provider-native tools, no computer use. Anything richer is a reason to
 * bring your own key, which stays free.
 *
 * <p>The per-install credential is minted by device-code pairing (T957), never
 * shipped in the distribution; this class only knows how to spend it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurVigletLlmProvider extends TurOpenAiCompatibleLlmProvider {

    /** Plugin type of the managed provider — matches the {@code VIGLET} vendor row. */
    public static final String PLUGIN_TYPE = "viglet";

    /** Vendor id seeded for this provider (see {@code TurLLMVendorOnStartup}). */
    public static final String VENDOR_ID = "VIGLET";

    private final TurVigletAiProperty vigletAiProperty;
    private final TurVigletInstallIdentityService installIdentityService;

    public TurVigletLlmProvider(TurProviderOptionsParser optionsParser,
            TurVigletAiProperty vigletAiProperty,
            TurVigletInstallIdentityService installIdentityService) {
        super(optionsParser);
        this.vigletAiProperty = vigletAiProperty;
        this.installIdentityService = installIdentityService;
    }

    /**
     * T960 / §XIV.10.5 — every managed call carries the install id and a
     * short-lived assertion signed with the keypair minted at pairing, so the
     * credential is spendable only from the install that earned it. Without this
     * the credit is a bearer token, and a bearer token that works from anywhere
     * is an inference API — the market this block exists not to enter.
     *
     * <p>An install that never paired signs nothing and sends nothing: the
     * managed side refuses it, which is the correct answer and a legible one.
     */
    @Override
    protected Map<String, String> extraHeaders(TurLLMInstance turLLMInstance,
            Map<String, Object> options) {
        return installIdentityService.assertionHeaders();
    }

    @Override
    public String getPluginType() {
        return PLUGIN_TYPE;
    }

    /** Fixed endpoint — the instance {@code url} and {@code baseUrl} option are ignored. */
    @Override
    protected String resolveBaseUrl(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        String baseUrl = vigletAiProperty.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(
                    "Missing turing.viglet-ai.base-url for the Viglet AI provider in LLM instance: "
                            + turLLMInstance.getId());
        }
        return baseUrl;
    }

    /** The opaque alias, never a vendor model name. */
    @Override
    protected String resolveChatModelName(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        return modelAlias(turLLMInstance);
    }

    /** Embeddings ride the same alias — the endpoint serves one id for both kinds. */
    @Override
    protected String resolveEmbeddingModelName(TurLLMInstance turLLMInstance, Map<String, Object> options) {
        return modelAlias(turLLMInstance);
    }

    /**
     * The whole catalogue: one id. Answered locally rather than by probing
     * {@code GET /v1/models} — the answer is fixed, and the picker must offer
     * the alias even before the install has been paired (T957) and has a
     * credential to authenticate with.
     */
    @Override
    public List<TurLlmModelOption> listModels(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        String alias = modelAlias(turLLMInstance);
        return List.of(new TurLlmModelOption(alias, alias, TurLlmModelKind.CHAT));
    }

    private String modelAlias(TurLLMInstance turLLMInstance) {
        String alias = vigletAiProperty.getModelAlias();
        if (!StringUtils.hasText(alias)) {
            throw new IllegalStateException(
                    "Missing turing.viglet-ai.model-alias for the Viglet AI provider in LLM instance: "
                            + (turLLMInstance == null ? "<none>" : turLLMInstance.getId()));
        }
        return alias;
    }
}
