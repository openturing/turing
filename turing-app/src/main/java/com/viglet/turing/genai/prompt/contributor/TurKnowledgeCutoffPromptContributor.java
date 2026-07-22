/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.prompt.contributor;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;
import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;

import lombok.extern.slf4j.Slf4j;

/**
 * T787 / §LIII.4 (Block BE) — opt-in system-prompt disclosure of the configured
 * model's knowledge cutoff. When the agent's {@code discloseKnowledgeCutoff} flag
 * is on and the catalog knows the model's {@code knowledgeCutoff}, this appends a
 * line so the model self-discloses how stale its knowledge may be. A no-op
 * segment for a default-off agent (byte-identical to before) and silent when the
 * cutoff is unknown.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurKnowledgeCutoffPromptContributor implements TurPromptContributor {

    private final TurLlmModelCatalog modelCatalog;

    public TurKnowledgeCutoffPromptContributor(TurLlmModelCatalog modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    @Override
    public int order() {
        return ORDER_KNOWLEDGE_CUTOFF;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        TurAIAgent agent = context.agent();
        if (agent == null || !agent.isDiscloseKnowledgeCutoff()) {
            return List.of();
        }
        String cutoff = resolveKnowledgeCutoff(agent);
        if (!StringUtils.hasText(cutoff)) {
            return List.of();
        }
        String text = "\n\nYour knowledge cutoff is " + cutoff
                + ". You have no reliable knowledge of events after this date; if asked about "
                + "something more recent, say so rather than guessing.";
        return List.of(TurPromptSegment.of(TurPromptSegment.ORIGIN_CAPABILITY,
                "knowledge-cutoff", text, TurPromptStability.STABLE));
    }

    /**
     * The catalog {@code knowledgeCutoff} of the agent's default (first enabled)
     * LLM model, or {@code null} when unknown. Best-effort: a lazy-load or lookup
     * failure degrades to "no disclosure" rather than breaking the turn.
     */
    private String resolveKnowledgeCutoff(TurAIAgent agent) {
        try {
            TurLLMInstance instance = firstEnabledInstance(agent);
            if (instance == null || !StringUtils.hasText(instance.getModelName())) {
                return null;
            }
            String slug = vendorSlug(instance.getTurLLMVendor());
            if (slug == null) {
                return null;
            }
            return modelCatalog.allModels().getOrDefault(slug, List.of()).stream()
                    .filter(o -> instance.getModelName().equals(o.id()))
                    .map(TurLlmModelOption::metadata)
                    .filter(m -> m != null && StringUtils.hasText(m.knowledgeCutoff()))
                    .map(TurLlmModelMetadata::knowledgeCutoff)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            log.debug("[KnowledgeCutoff] could not resolve cutoff for agent {}: {}",
                    agent.getId(), e.getMessage());
            return null;
        }
    }

    private static TurLLMInstance firstEnabledInstance(TurAIAgent agent) {
        if (agent.getLlmInstances() == null) {
            return null;
        }
        return agent.getLlmInstances().stream()
                .filter(i -> i != null && i.getEnabled() == 1)
                .findFirst()
                .orElse(null);
    }

    private static String vendorSlug(TurLLMVendor vendor) {
        if (vendor == null) {
            return null;
        }
        String slug = StringUtils.hasText(vendor.getPlugin()) ? vendor.getPlugin() : vendor.getId();
        return StringUtils.hasText(slug) ? slug.toLowerCase(Locale.ROOT) : null;
    }
}
