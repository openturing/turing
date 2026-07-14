package com.viglet.turing.api.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.system.TurEmailProviderType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;

class TurGlobalSettingsBeanTest {

    @Test
    void shouldCreateUsingBuilder() {
        TurGlobalSettingsBean bean = TurGlobalSettingsBean.builder()
                .decimalSeparator(TurGlobalDecimalSeparator.COMMA)
                .emailProvider(TurEmailProviderType.BREVO)
                .build();

        assertThat(bean.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.COMMA);
        assertThat(bean.getEmailProvider()).isEqualTo(TurEmailProviderType.BREVO);
    }

    @Test
    void shouldCreateUsingAllArgsConstructor() {
        TurGlobalSettingsBean bean = new TurGlobalSettingsBean(
                TurGlobalDecimalSeparator.DOT,
                null,  // pythonExecutable
                null,  // pythonRequirements (new in 2026.2.7)
                null,  // codeInterpreterExecutionMode (T80)
                null,  // codeInterpreterDockerImage (T80)
                null,  // codeInterpreterSkillImage (T321)
                null,  // defaultLlmId
                null,  // modelLaneFastId (T517)
                null,  // modelLaneReasoningId (T517)
                null,  // modelLaneCheapId (T517)
                null,  // llmFallbackChainIds (T518)
                null,  // llmFallbackMode (T518)
                false, // secondOpinionEnabled (T522)
                null,  // secondOpinionLlmId (T522)
                false, 0L, false,
                TurEmailProviderType.BREVO, null, null, null, null,
                false, null, null, null,
                24,  // piiSlotTtlHours (T61)
                false, 20, null, null, null,  // rag SN rerank enabled/topN/strategy/endpoint/model
                null, null, null,  // ragSnRerank region / vertexProject / vertexLocation (T521)
                null, false,  // ragSnRerankApiKey / apiKeySet (T339)
                false,  // ragSnRerankCacheEnabled (T341)
                null, null, null,  // transcriptionStrategy / endpoint / model (T687)
                0L,  // transcriptionMaxUploadBytes (T687)
                null, false,  // transcriptionApiKey / apiKeySet (T687)
                null, null,  // urlFetchMode / browserlessUrl (T739)
                null, false);  // urlFetchBrowserlessToken / tokenSet (T739)

        assertThat(bean.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
        assertThat(bean.getEmailProvider()).isEqualTo(TurEmailProviderType.BREVO);
    }

    @Test
    void shouldSupportNoArgsConstructorAndSetter() {
        TurGlobalSettingsBean bean = new TurGlobalSettingsBean();

        assertThat(bean.getDecimalSeparator()).isNull();

        bean.setDecimalSeparator(TurGlobalDecimalSeparator.COMMA);

        assertThat(bean.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.COMMA);
    }
}
