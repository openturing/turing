package com.viglet.turing.persistence.model.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TurLLMTokenUsageTest {

    @Test
    void shouldSetAndGetAllFields() {
        TurLLMTokenUsage entity = new TurLLMTokenUsage();

        entity.setId("token-1");
        entity.setVendorId("openai");
        entity.setModelName("gpt-4");
        entity.setUsername("admin");
        entity.setInputTokens(100L);
        entity.setOutputTokens(50L);
        entity.setTotalTokens(150L);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);

        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        entity.setTurLLMInstance(instance);

        assertThat(entity.getId()).isEqualTo("token-1");
        assertThat(entity.getVendorId()).isEqualTo("openai");
        assertThat(entity.getModelName()).isEqualTo("gpt-4");
        assertThat(entity.getUsername()).isEqualTo("admin");
        assertThat(entity.getInputTokens()).isEqualTo(100L);
        assertThat(entity.getOutputTokens()).isEqualTo(50L);
        assertThat(entity.getTotalTokens()).isEqualTo(150L);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getTurLLMInstance()).isEqualTo(instance);
    }

    @Test
    void shouldHaveDefaultValues() {
        TurLLMTokenUsage entity = new TurLLMTokenUsage();

        assertThat(entity.getId()).isNull();
        assertThat(entity.getVendorId()).isNull();
        assertThat(entity.getModelName()).isNull();
        assertThat(entity.getUsername()).isNull();
        assertThat(entity.getInputTokens()).isZero();
        assertThat(entity.getOutputTokens()).isZero();
        assertThat(entity.getTotalTokens()).isZero();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getTurLLMInstance()).isNull();
    }

    @Test
    void shouldHandleLargeTokenValues() {
        TurLLMTokenUsage entity = new TurLLMTokenUsage();

        entity.setInputTokens(Long.MAX_VALUE);
        entity.setOutputTokens(Long.MAX_VALUE);
        entity.setTotalTokens(Long.MAX_VALUE);

        assertThat(entity.getInputTokens()).isEqualTo(Long.MAX_VALUE);
        assertThat(entity.getOutputTokens()).isEqualTo(Long.MAX_VALUE);
        assertThat(entity.getTotalTokens()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldSetVendorIdIndependently() {
        TurLLMTokenUsage entity = new TurLLMTokenUsage();
        entity.setVendorId("anthropic");

        assertThat(entity.getVendorId()).isEqualTo("anthropic");
        assertThat(entity.getTurLLMInstance()).isNull();
    }
}
