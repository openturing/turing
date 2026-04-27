package com.viglet.turing.persistence.model.sn.genai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

@ExtendWith(MockitoExtension.class)
class TurSNSiteGenAiTest {

    @Mock
    private TurAIAgent turAIAgent;

    private TurSNSiteGenAi turSNSiteGenAi;

    @BeforeEach
    void setUp() {
        turSNSiteGenAi = new TurSNSiteGenAi();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        turSNSiteGenAi.setId("genai-id");
        turSNSiteGenAi.setTurAIAgent(turAIAgent);
        turSNSiteGenAi.setSitePrompt("Site description.");

        assertThat(turSNSiteGenAi.getId()).isEqualTo("genai-id");
        assertThat(turSNSiteGenAi.getTurAIAgent()).isEqualTo(turAIAgent);
        assertThat(turSNSiteGenAi.getSitePrompt()).isEqualTo("Site description.");
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        assertThat(turSNSiteGenAi.getId()).isNull();
        assertThat(turSNSiteGenAi.getTurAIAgent()).isNull();
        assertThat(turSNSiteGenAi.getSitePrompt()).isNull();
    }
}
