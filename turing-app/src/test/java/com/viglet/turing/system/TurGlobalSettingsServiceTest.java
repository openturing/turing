package com.viglet.turing.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

/**
 * Tests for TurGlobalSettingsService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurGlobalSettingsServiceTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;
    @InjectMocks
    private TurGlobalSettingsService service;

    // --- T80 Code Interpreter execution mode / docker image ---

    @Test
    void getCodeInterpreterExecutionModeShouldDefaultToNative() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE))
                .thenReturn(Optional.empty());
        assertEquals(com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode.NATIVE,
                service.getCodeInterpreterExecutionMode());
    }

    @Test
    void getCodeInterpreterExecutionModeShouldReturnDockerWhenConfigured() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("DOCKER");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE))
                .thenReturn(Optional.of(configVar));
        assertEquals(com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode.DOCKER,
                service.getCodeInterpreterExecutionMode());
    }

    @Test
    void getCodeInterpreterExecutionModeShouldFallBackToNativeForGarbage() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("NONSENSE");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE))
                .thenReturn(Optional.of(configVar));
        assertEquals(com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode.NATIVE,
                service.getCodeInterpreterExecutionMode());
    }

    @Test
    void updateCodeInterpreterExecutionModeShouldPersistName() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE))
                .thenReturn(Optional.empty());
        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        service.updateCodeInterpreterExecutionMode(
                com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode.DOCKER);
        verify(turConfigVarRepository).save(captor.capture());
        assertEquals("DOCKER", captor.getValue().getValue());
        assertEquals(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE, captor.getValue().getId());
    }

    @Test
    void updateCodeInterpreterExecutionModeShouldCoerceNullToNative() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE))
                .thenReturn(Optional.empty());
        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        service.updateCodeInterpreterExecutionMode(null);
        verify(turConfigVarRepository).save(captor.capture());
        assertEquals("NATIVE", captor.getValue().getValue());
    }

    @Test
    void getCodeInterpreterDockerImageShouldDefaultWhenMissing() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE))
                .thenReturn(Optional.empty());
        assertEquals("python:3.12-slim", service.getCodeInterpreterDockerImage());
    }

    @Test
    void getCodeInterpreterDockerImageShouldDefaultWhenBlank() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("   ");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE))
                .thenReturn(Optional.of(configVar));
        assertEquals("python:3.12-slim", service.getCodeInterpreterDockerImage());
    }

    @Test
    void updateCodeInterpreterDockerImageShouldTrimAndPersist() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE))
                .thenReturn(Optional.empty());
        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        String result = service.updateCodeInterpreterDockerImage("  my/image:1  ");
        verify(turConfigVarRepository).save(captor.capture());
        assertEquals("my/image:1", captor.getValue().getValue());
        assertEquals("my/image:1", result);
    }

    @Test
    void updateCodeInterpreterDockerImageShouldFallBackToDefaultForBlank() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE))
                .thenReturn(Optional.empty());
        String result = service.updateCodeInterpreterDockerImage("");
        assertEquals("python:3.12-slim", result);
    }

    // --- getDecimalSeparator ---

    @Test
    void getDecimalSeparatorShouldReturnDotByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.empty());
        assertEquals(TurGlobalDecimalSeparator.DOT, service.getDecimalSeparator());
    }

    @Test
    void getDecimalSeparatorShouldReturnCommaWhenConfigured() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("COMMA");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(configVar));
        assertEquals(TurGlobalDecimalSeparator.COMMA, service.getDecimalSeparator());
    }

    @Test
    void getDecimalSeparatorShouldReturnDotForInvalidValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("INVALID");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(configVar));
        assertEquals(TurGlobalDecimalSeparator.DOT, service.getDecimalSeparator());
    }

    @Test
    void getDecimalSeparatorShouldHandleLowercaseValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("comma");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(configVar));
        assertEquals(TurGlobalDecimalSeparator.COMMA, service.getDecimalSeparator());
    }

    @Test
    void getDecimalSeparatorShouldTrimWhitespace() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("  DOT  ");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(configVar));
        assertEquals(TurGlobalDecimalSeparator.DOT, service.getDecimalSeparator());
    }

    // --- updateDecimalSeparator ---

    @Test
    void updateDecimalSeparatorShouldSaveAndReturn() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TurGlobalDecimalSeparator result = service.updateDecimalSeparator(TurGlobalDecimalSeparator.COMMA);

        assertEquals(TurGlobalDecimalSeparator.COMMA, result);
        verify(turConfigVarRepository).save(any(TurConfigVar.class));
    }

    @Test
    void updateDecimalSeparatorShouldUpdateExistingVar() {
        TurConfigVar existing = new TurConfigVar();
        existing.setId(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        existing.setValue("DOT");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(existing));
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateDecimalSeparator(TurGlobalDecimalSeparator.COMMA);

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository).save(captor.capture());
        assertEquals("COMMA", captor.getValue().getValue());
    }

    // --- getPythonExecutable ---

    @Test
    void getPythonExecutableShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE))
                .thenReturn(Optional.empty());
        assertEquals("", service.getPythonExecutable());
    }

    @Test
    void getPythonExecutableShouldReturnConfiguredValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("/usr/bin/python3");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE))
                .thenReturn(Optional.of(configVar));
        assertEquals("/usr/bin/python3", service.getPythonExecutable());
    }

    // --- updatePythonExecutable ---

    @Test
    void updatePythonExecutableShouldTrimAndSave() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.updatePythonExecutable("  /usr/bin/python3  ");

        assertEquals("/usr/bin/python3", result);
    }

    @Test
    void updatePythonExecutableShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.updatePythonExecutable(null);

        assertEquals("", result);
    }

    // --- getDefaultLlmId ---

    @Test
    void getDefaultLlmIdShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM))
                .thenReturn(Optional.empty());
        assertEquals("", service.getDefaultLlmId());
    }

    @Test
    void getDefaultLlmIdShouldReturnConfiguredValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("llm-instance-1");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM))
                .thenReturn(Optional.of(configVar));
        assertEquals("llm-instance-1", service.getDefaultLlmId());
    }

    // --- updateDefaultLlmId ---

    @Test
    void updateDefaultLlmIdShouldTrimAndSave() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("llm-1", service.updateDefaultLlmId("  llm-1  "));
    }

    @Test
    void updateDefaultLlmIdShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("", service.updateDefaultLlmId(null));
    }

    // --- getEmailProvider ---

    @Test
    void getEmailProviderShouldReturnBrevoByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER))
                .thenReturn(Optional.empty());
        assertEquals(TurEmailProviderType.BREVO, service.getEmailProvider());
    }

    @Test
    void getEmailProviderShouldReturnBrevoForInvalidValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("INVALID_PROVIDER");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER))
                .thenReturn(Optional.of(configVar));
        assertEquals(TurEmailProviderType.BREVO, service.getEmailProvider());
    }

    @Test
    void getEmailProviderShouldBeCaseInsensitive() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("brevo");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER))
                .thenReturn(Optional.of(configVar));
        assertEquals(TurEmailProviderType.BREVO, service.getEmailProvider());
    }

    // --- updateEmailProvider ---

    @Test
    void updateEmailProviderShouldSaveAndReturn() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(TurEmailProviderType.BREVO, service.updateEmailProvider(TurEmailProviderType.BREVO));
        verify(turConfigVarRepository).save(any(TurConfigVar.class));
    }

    // --- getEmailApiKey ---

    @Test
    void getEmailApiKeyShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY))
                .thenReturn(Optional.empty());
        assertEquals("", service.getEmailApiKey());
    }

    @Test
    void getEmailApiKeyShouldReturnConfiguredValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("secret-key-123");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY))
                .thenReturn(Optional.of(configVar));
        assertEquals("secret-key-123", service.getEmailApiKey());
    }

    // --- updateEmailApiKey ---

    @Test
    void updateEmailApiKeyShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("", service.updateEmailApiKey(null));
    }

    @Test
    void updateEmailApiKeyShouldTrimValue() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("key", service.updateEmailApiKey("  key  "));
    }

    // --- getSenderEmail / getSenderName ---

    @Test
    void getSenderEmailShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL))
                .thenReturn(Optional.empty());
        assertEquals("", service.getSenderEmail());
    }

    @Test
    void getSenderNameShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME))
                .thenReturn(Optional.empty());
        assertEquals("", service.getSenderName());
    }

    @Test
    void getSenderNameShouldReturnConfiguredValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("Viglet Turing");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME))
                .thenReturn(Optional.of(configVar));
        assertEquals("Viglet Turing", service.getSenderName());
    }

    // --- updateSenderEmail / updateSenderName ---

    @Test
    void updateSenderEmailShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("", service.updateSenderEmail(null));
    }

    @Test
    void updateSenderNameShouldTrimValue() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("Turing", service.updateSenderName("  Turing  "));
    }

    // --- getRecipientEmail ---

    @Test
    void getRecipientEmailShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL))
                .thenReturn(Optional.empty());
        assertEquals("", service.getRecipientEmail());
    }

    @Test
    void updateRecipientEmailShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("", service.updateRecipientEmail(null));
    }

    @Test
    void updateRecipientEmailShouldTrimValue() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("admin@test.com", service.updateRecipientEmail("  admin@test.com  "));
    }

    // --- isLlmCacheEnabled ---

    @Test
    void isLlmCacheEnabledShouldReturnFalseByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.empty());
        assertFalse(service.isLlmCacheEnabled());
    }

    @Test
    void isLlmCacheEnabledShouldReturnTrueWhenConfigured() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("true");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.of(configVar));
        assertTrue(service.isLlmCacheEnabled());
    }

    @Test
    void isLlmCacheEnabledShouldReturnFalseForNonBooleanString() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("yes");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.of(configVar));
        assertFalse(service.isLlmCacheEnabled());
    }

    // --- updateLlmCacheEnabled ---

    @Test
    void updateLlmCacheEnabledShouldSaveAndReturnTrue() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.updateLlmCacheEnabled(true));

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository).save(captor.capture());
        assertEquals("true", captor.getValue().getValue());
    }

    @Test
    void updateLlmCacheEnabledShouldSaveAndReturnFalse() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertFalse(service.updateLlmCacheEnabled(false));
    }

    // --- getLlmCacheTtlMs ---

    @Test
    void getLlmCacheTtlMsShouldReturnDefaultWhenNotSet() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.empty());
        assertEquals(3_600_000L, service.getLlmCacheTtlMs());
    }

    @Test
    void getLlmCacheTtlMsShouldParseConfiguredValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("5000");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.of(configVar));
        assertEquals(5000L, service.getLlmCacheTtlMs());
    }

    @Test
    void getLlmCacheTtlMsShouldReturnDefaultForInvalidValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("notanumber");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.of(configVar));
        assertEquals(3_600_000L, service.getLlmCacheTtlMs());
    }

    @Test
    void getLlmCacheTtlMsShouldHandleWhitespace() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("  7200000  ");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.of(configVar));
        assertEquals(7_200_000L, service.getLlmCacheTtlMs());
    }

    // --- updateLlmCacheTtlMs ---

    @Test
    void updateLlmCacheTtlMsShouldClampNegativeToZero() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long result = service.updateLlmCacheTtlMs(-100);
        assertEquals(0L, result);
    }

    @Test
    void updateLlmCacheTtlMsShouldAcceptZero() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(0L, service.updateLlmCacheTtlMs(0));
    }

    @Test
    void updateLlmCacheTtlMsShouldAcceptPositiveValue() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(60000L, service.updateLlmCacheTtlMs(60000));
    }

    // --- isLlmCacheRegenerate ---

    @Test
    void isLlmCacheRegenerateShouldReturnFalseByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE))
                .thenReturn(Optional.empty());
        assertFalse(service.isLlmCacheRegenerate());
    }

    @Test
    void isLlmCacheRegenerateShouldReturnTrueWhenConfigured() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("true");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE))
                .thenReturn(Optional.of(configVar));
        assertTrue(service.isLlmCacheRegenerate());
    }

    // --- updateLlmCacheRegenerate ---

    @Test
    void updateLlmCacheRegenerateShouldSaveAndReturn() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.updateLlmCacheRegenerate(true));
    }

    // --- isRagEnabled ---

    @Test
    void isRagEnabledShouldReturnFalseByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED))
                .thenReturn(Optional.empty());
        assertFalse(service.isRagEnabled());
    }

    @Test
    void isRagEnabledShouldReturnTrueWhenConfigured() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("true");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED))
                .thenReturn(Optional.of(configVar));
        assertTrue(service.isRagEnabled());
    }

    // --- updateRagEnabled ---

    @Test
    void updateRagEnabledShouldSaveAndReturn() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.updateRagEnabled(true));
        assertFalse(service.updateRagEnabled(false));
    }

    // --- getDefaultEmbeddingModelId ---

    @Test
    void getDefaultEmbeddingModelIdShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID))
                .thenReturn(Optional.empty());
        assertEquals("", service.getDefaultEmbeddingModelId());
    }

    @Test
    void getDefaultEmbeddingModelIdShouldReturnConfiguredValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue("embed-model-1");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID))
                .thenReturn(Optional.of(configVar));
        assertEquals("embed-model-1", service.getDefaultEmbeddingModelId());
    }

    // --- updateDefaultEmbeddingModelId ---

    @Test
    void updateDefaultEmbeddingModelIdShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("", service.updateDefaultEmbeddingModelId(null));
    }

    @Test
    void updateDefaultEmbeddingModelIdShouldTrimAndSave() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("model-1", service.updateDefaultEmbeddingModelId("  model-1  "));
    }

    // --- getDefaultEmbeddingStoreId ---

    @Test
    void getDefaultEmbeddingStoreIdShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID))
                .thenReturn(Optional.empty());
        assertEquals("", service.getDefaultEmbeddingStoreId());
    }

    // --- updateDefaultEmbeddingStoreId ---

    @Test
    void updateDefaultEmbeddingStoreIdShouldHandleNull() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("", service.updateDefaultEmbeddingStoreId(null));
    }

    @Test
    void updateDefaultEmbeddingStoreIdShouldTrimAndSave() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("store-1", service.updateDefaultEmbeddingStoreId("  store-1  "));
    }

    // --- update methods save correct path ---

    @Test
    void updateMethodsShouldSetCorrectIdAndPath() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateSenderEmail("test@example.com");

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository).save(captor.capture());

        TurConfigVar saved = captor.getValue();
        assertEquals(TurConfigVarOnStartup.SENDER_EMAIL, saved.getId());
        assertEquals(TurConfigVarOnStartup.GLOBAL_PATH, saved.getPath());
        assertEquals("test@example.com", saved.getValue());
    }
}
