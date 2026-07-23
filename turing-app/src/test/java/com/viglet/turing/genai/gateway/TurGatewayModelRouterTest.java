/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * T744 / §XLIX — unit coverage for gateway model-name routing detection and the
 * embedded-instance resolution (the branches with no live LLM dependency).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayModelRouterTest {

    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurAgentChatExecutor agentChatExecutor;
    @Mock
    private TurSNSearchProcess snSearchProcess;
    @Mock
    private TurGenAiContextFactory contextFactory;
    @Mock
    private TurSNGenAi snGenAi;
    @Mock
    private com.viglet.turing.genai.catalog.TurCatalogCopilotService catalogCopilotService;

    private TurGatewayModelRouter router() {
        return new TurGatewayModelRouter(agentRepository, llmInstanceRepository, agentChatExecutor,
                snSearchProcess, contextFactory, snGenAi, catalogCopilotService);
    }

    @Test
    void detectsPrefixes() {
        TurGatewayModelRouter r = router();
        assertThat(r.isAgent("turing-agent:abc")).isTrue();
        assertThat(r.isSn("turing-sn:wknd")).isTrue();
        assertThat(r.isLocal("turing-local:phi")).isTrue();
        assertThat(r.isCopilot("turing-copilot:model-catalog")).isTrue();
        assertThat(r.isNative("turing-agent:abc")).isTrue();
        assertThat(r.isNative("turing-sn:wknd")).isTrue();
        assertThat(r.isNative("turing-copilot:model-catalog")).isTrue();
        assertThat(r.isNative("turing-local:phi")).isFalse();
        assertThat(r.isNative("gpt-4o")).isFalse();
        assertThat(r.isAgent(null)).isFalse();
        assertThat(r.isCopilot(null)).isFalse();
    }

    @Test
    void copilotAnswerAppendsCitedSources() {
        var citations = List.of(
                new com.viglet.turing.genai.catalog.TurCatalogCitation(1, "m1", "Model One",
                        "https://x/m1", 1.0, null),
                new com.viglet.turing.genai.catalog.TurCatalogCitation(2, "m2", "Model Two", null, 0.9, null));
        when(catalogCopilotService.answer(org.mockito.ArgumentMatchers.eq("model-catalog"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(com.viglet.turing.genai.catalog.TurCatalogCopilotResult.ok(
                        "The cheapest is Model One [1].", citations, "kind=EMBEDDING", 2L));

        String answer = router().copilotAnswer("model-catalog", List.of(
                new com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage("user", "cheapest?")));

        assertThat(answer).contains("The cheapest is Model One [1].");
        assertThat(answer).contains("Sources:");
        assertThat(answer).contains("[1] Model One — https://x/m1");
        assertThat(answer).contains("[2] Model Two"); // no url → no dash suffix
    }

    @Test
    void copilotAnswerThrowsWhenUnavailable() {
        when(catalogCopilotService.answer(org.mockito.ArgumentMatchers.eq("ghost"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(com.viglet.turing.genai.catalog.TurCatalogCopilotResult.error("no LLM"));

        assertThatThrownBy(() -> router().copilotAnswer("ghost", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no LLM");
    }

    @Test
    void copilotAnswerRejectsBlankSite() {
        assertThatThrownBy(() -> router().copilotAnswer("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TurLLMInstance instance(String id, String plugin, int enabled) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        i.setEnabled(enabled);
        TurLLMVendor v = new TurLLMVendor();
        v.setId("v-" + id);
        v.setPlugin(plugin);
        i.setTurLLMVendor(v);
        return i;
    }

    @Test
    void resolvesEnabledEmbeddedInstance() {
        when(llmInstanceRepository.findAll()).thenReturn(List.of(
                instance("i-openai", "openai", 1),
                instance("i-embed", "embedded-jlama", 1)));

        assertThat(router().resolveEmbeddedInstance().getId()).isEqualTo("i-embed");
    }

    @Test
    void throwsWhenNoEmbeddedInstance() {
        when(llmInstanceRepository.findAll()).thenReturn(List.of(instance("i-openai", "openai", 1)));

        assertThatThrownBy(() -> router().resolveEmbeddedInstance())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedded");
    }

    @Test
    void retrieveContextReturnsNullForBlankArgs() {
        assertThat(router().retrieveContext(null, "q")).isNull();
        assertThat(router().retrieveContext("site", " ")).isNull();
    }
}
