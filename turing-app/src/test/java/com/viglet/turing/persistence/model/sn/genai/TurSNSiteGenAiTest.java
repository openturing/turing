package com.viglet.turing.persistence.model.sn.genai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

@ExtendWith(MockitoExtension.class)
class TurSNSiteGenAiTest {

    @Mock
    private TurLLMInstance turLLMInstance;

    @Mock
    private TurStoreInstance turStoreInstance;

    private TurSNSiteGenAi turSNSiteGenAi;

    @BeforeEach
    void setUp() {
        turSNSiteGenAi = new TurSNSiteGenAi();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        turSNSiteGenAi.setId("genai-id");
        turSNSiteGenAi.setEnabled(true);
        turSNSiteGenAi.setTurLLMInstance(turLLMInstance);
        turSNSiteGenAi.setTurStoreInstance(turStoreInstance);
        turSNSiteGenAi.setSystemPrompt("You are a search assistant.");

        assertThat(turSNSiteGenAi.getId()).isEqualTo("genai-id");
        assertThat(turSNSiteGenAi.isEnabled()).isTrue();
        assertThat(turSNSiteGenAi.getTurLLMInstance()).isEqualTo(turLLMInstance);
        assertThat(turSNSiteGenAi.getTurStoreInstance()).isEqualTo(turStoreInstance);
        assertThat(turSNSiteGenAi.getSystemPrompt()).isEqualTo("You are a search assistant.");
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        assertThat(turSNSiteGenAi.getId()).isNull();
        assertThat(turSNSiteGenAi.isEnabled()).isFalse();
        assertThat(turSNSiteGenAi.getTurLLMInstance()).isNull();
        assertThat(turSNSiteGenAi.getTurStoreInstance()).isNull();
        assertThat(turSNSiteGenAi.getSystemPrompt()).isNull();
    }
}
