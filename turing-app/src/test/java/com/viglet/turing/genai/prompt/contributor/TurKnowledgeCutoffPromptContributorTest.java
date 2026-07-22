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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;

/**
 * T787 / §LIII.4 — unit tests for the opt-in knowledge-cutoff prompt contributor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurKnowledgeCutoffPromptContributorTest {

    @Mock
    private TurLlmModelCatalog modelCatalog;

    private TurAIAgent agent(boolean disclose, String vendorId, String modelName) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setDiscloseKnowledgeCutoff(disclose);
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(vendorId);
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(1);
        instance.setModelName(modelName);
        instance.setTurLLMVendor(vendor);
        agent.setLlmInstances(Set.of(instance));
        return agent;
    }

    private TurPromptAssemblyContext ctx(TurAIAgent agent) {
        return new TurPromptAssemblyContext(agent, null, null, null, null, null, null);
    }

    private void stubCatalog(String vendorSlug, String modelName, String cutoff) {
        TurLlmModelMetadata meta = new TurLlmModelMetadata(null, null, null, null, null, null, null, null,
                cutoff, null, null, null, null, null);
        lenient().when(modelCatalog.allModels()).thenReturn(Map.of(vendorSlug,
                List.of(new TurLlmModelOption(modelName, modelName, TurLlmModelKind.CHAT, meta))));
    }

    @Test
    void emitsNothingWhenToggleOff() {
        TurKnowledgeCutoffPromptContributor contributor = new TurKnowledgeCutoffPromptContributor(modelCatalog);
        assertThat(contributor.contribute(ctx(agent(false, "OPENAI", "gpt-4o")))).isEmpty();
    }

    @Test
    void injectsCutoffLineWhenToggleOnAndCatalogKnowsIt() {
        stubCatalog("openai", "gpt-4o", "2024-10");
        TurKnowledgeCutoffPromptContributor contributor = new TurKnowledgeCutoffPromptContributor(modelCatalog);

        List<TurPromptSegment> segments = contributor.contribute(ctx(agent(true, "OPENAI", "gpt-4o")));

        assertThat(segments).singleElement().satisfies(s ->
                assertThat(s.text()).contains("Your knowledge cutoff is 2024-10"));
    }

    @Test
    void emitsNothingWhenCatalogHasNoCutoff() {
        stubCatalog("openai", "gpt-4o", null);
        TurKnowledgeCutoffPromptContributor contributor = new TurKnowledgeCutoffPromptContributor(modelCatalog);
        assertThat(contributor.contribute(ctx(agent(true, "OPENAI", "gpt-4o")))).isEmpty();
    }

    @Test
    void orderIsAfterCapabilityBlocks() {
        TurKnowledgeCutoffPromptContributor contributor = new TurKnowledgeCutoffPromptContributor(modelCatalog);
        assertThat(contributor.order()).isEqualTo(TurKnowledgeCutoffPromptContributor.ORDER_KNOWLEDGE_CUTOFF);
        assertThat(contributor.order()).isGreaterThan(TurKnowledgeCutoffPromptContributor.ORDER_CAPABILITY);
    }
}
