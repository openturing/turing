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

    private TurGatewayModelRouter router() {
        return new TurGatewayModelRouter(agentRepository, llmInstanceRepository, agentChatExecutor,
                snSearchProcess, contextFactory, snGenAi);
    }

    @Test
    void detectsPrefixes() {
        TurGatewayModelRouter r = router();
        assertThat(r.isAgent("turing-agent:abc")).isTrue();
        assertThat(r.isSn("turing-sn:wknd")).isTrue();
        assertThat(r.isLocal("turing-local:phi")).isTrue();
        assertThat(r.isNative("turing-agent:abc")).isTrue();
        assertThat(r.isNative("turing-sn:wknd")).isTrue();
        assertThat(r.isNative("turing-local:phi")).isFalse();
        assertThat(r.isNative("gpt-4o")).isFalse();
        assertThat(r.isAgent(null)).isFalse();
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
