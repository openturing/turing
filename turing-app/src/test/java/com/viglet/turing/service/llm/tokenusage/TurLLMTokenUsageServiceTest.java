package com.viglet.turing.service.llm.tokenusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMTokenUsage;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

@ExtendWith(MockitoExtension.class)
class TurLLMTokenUsageServiceTest {

    @Mock
    private TurLLMTokenUsageRepository tokenUsageRepository;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private ChatResponseMetadata metadata;

    @Mock
    private Usage usage;

    private TurLLMTokenUsageService service;
    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        TurLlmObservation observation = new TurLlmObservation(ObservationRegistry.NOOP, new SimpleMeterRegistry());
        com.viglet.turing.tenant.TurTenantContext tenantContext =
                new com.viglet.turing.tenant.TurTenantContext(
                        new com.viglet.turing.properties.TurConfigProperties());
        service = new TurLLMTokenUsageService(tokenUsageRepository, observation, tenantContext);

        instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setModelName("gpt-4");
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("openai");
        instance.setTurLLMVendor(vendor);
    }

    @Test
    void shouldRecordUsageSuccessfully() {
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(100);
        when(usage.getCompletionTokens()).thenReturn(50);
        when(usage.getTotalTokens()).thenReturn(150);

        service.recordUsage(instance, chatResponse, "admin");

        ArgumentCaptor<TurLLMTokenUsage> captor = ArgumentCaptor.forClass(TurLLMTokenUsage.class);
        verify(tokenUsageRepository).save(captor.capture());

        TurLLMTokenUsage saved = captor.getValue();
        assertThat(saved.getTurLLMInstance()).isEqualTo(instance);
        assertThat(saved.getVendorId()).isEqualTo("openai");
        assertThat(saved.getModelName()).isEqualTo("gpt-4");
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getInputTokens()).isEqualTo(100L);
        assertThat(saved.getOutputTokens()).isEqualTo(50L);
        assertThat(saved.getTotalTokens()).isEqualTo(150L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCalculateTotalWhenTotalTokensIsZero() {
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(200);
        when(usage.getCompletionTokens()).thenReturn(80);
        when(usage.getTotalTokens()).thenReturn(0);

        service.recordUsage(instance, chatResponse, "user1");

        ArgumentCaptor<TurLLMTokenUsage> captor = ArgumentCaptor.forClass(TurLLMTokenUsage.class);
        verify(tokenUsageRepository).save(captor.capture());

        assertThat(captor.getValue().getTotalTokens()).isEqualTo(280L);
    }

    @Test
    void shouldNotRecordWhenResponseIsNull() {
        service.recordUsage(instance, null, "admin");

        verify(tokenUsageRepository, never()).save(any());
    }

    @Test
    void shouldNotRecordWhenMetadataIsNull() {
        when(chatResponse.getMetadata()).thenReturn(null);

        service.recordUsage(instance, chatResponse, "admin");

        verify(tokenUsageRepository, never()).save(any());
    }

    @Test
    void shouldNotRecordWhenUsageIsNull() {
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(null);

        service.recordUsage(instance, chatResponse, "admin");

        verify(tokenUsageRepository, never()).save(any());
    }

    @Test
    void shouldNotRecordWhenAllTokensAreZero() {
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(0);
        when(usage.getCompletionTokens()).thenReturn(0);
        when(usage.getTotalTokens()).thenReturn(0);

        service.recordUsage(instance, chatResponse, "admin");

        verify(tokenUsageRepository, never()).save(any());
    }

    @Test
    void shouldHandleNullVendor() {
        instance.setTurLLMVendor(null);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(usage.getTotalTokens()).thenReturn(15);

        service.recordUsage(instance, chatResponse, "admin");

        ArgumentCaptor<TurLLMTokenUsage> captor = ArgumentCaptor.forClass(TurLLMTokenUsage.class);
        verify(tokenUsageRepository).save(captor.capture());

        assertThat(captor.getValue().getVendorId()).isEqualTo("unknown");
    }

    @Test
    void shouldNotThrowWhenRepositorySaveFails() {
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(usage.getTotalTokens()).thenReturn(15);
        when(tokenUsageRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThat(service).isNotNull();
        service.recordUsage(instance, chatResponse, "admin");
        // Should not throw - exception is caught internally
    }
}
