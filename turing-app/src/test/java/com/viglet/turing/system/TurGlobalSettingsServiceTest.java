package com.viglet.turing.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@ExtendWith(MockitoExtension.class)
class TurGlobalSettingsServiceTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;
    @InjectMocks
    private TurGlobalSettingsService service;

    @Test
    void getDecimalSeparatorShouldReturnDotByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.empty());
        assertEquals(TurGlobalDecimalSeparator.DOT, service.getDecimalSeparator());
    }

    @Test
    void getDecimalSeparatorShouldReturnCommaWhenConfigured() {
        TurConfigVar var = new TurConfigVar();
        var.setValue("COMMA");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(var));
        assertEquals(TurGlobalDecimalSeparator.COMMA, service.getDecimalSeparator());
    }

    @Test
    void getDecimalSeparatorShouldReturnDotForInvalidValue() {
        TurConfigVar var = new TurConfigVar();
        var.setValue("INVALID");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(var));
        assertEquals(TurGlobalDecimalSeparator.DOT, service.getDecimalSeparator());
    }

    @Test
    void getPythonExecutableShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE))
                .thenReturn(Optional.empty());
        assertEquals("", service.getPythonExecutable());
    }

    @Test
    void getDefaultLlmIdShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM))
                .thenReturn(Optional.empty());
        assertEquals("", service.getDefaultLlmId());
    }

    @Test
    void getEmailProviderShouldReturnBrevoByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER))
                .thenReturn(Optional.empty());
        assertEquals(TurEmailProviderType.BREVO, service.getEmailProvider());
    }

    @Test
    void isLlmCacheEnabledShouldReturnFalseByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.empty());
        assertFalse(service.isLlmCacheEnabled());
    }

    @Test
    void isLlmCacheEnabledShouldReturnTrueWhenConfigured() {
        TurConfigVar var = new TurConfigVar();
        var.setValue("true");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED))
                .thenReturn(Optional.of(var));
        assertTrue(service.isLlmCacheEnabled());
    }

    @Test
    void getLlmCacheTtlMsShouldReturnDefaultWhenNotSet() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.empty());
        assertEquals(3_600_000L, service.getLlmCacheTtlMs());
    }

    @Test
    void getLlmCacheTtlMsShouldParseConfiguredValue() {
        TurConfigVar var = new TurConfigVar();
        var.setValue("5000");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.of(var));
        assertEquals(5000L, service.getLlmCacheTtlMs());
    }

    @Test
    void getLlmCacheTtlMsShouldReturnDefaultForInvalidValue() {
        TurConfigVar var = new TurConfigVar();
        var.setValue("notanumber");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.of(var));
        assertEquals(3_600_000L, service.getLlmCacheTtlMs());
    }

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
    void updateLlmCacheTtlMsShouldClampNegativeToZero() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long result = service.updateLlmCacheTtlMs(-100);
        assertEquals(0L, result);
    }

    @Test
    void getEmailApiKeyShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY))
                .thenReturn(Optional.empty());
        assertEquals("", service.getEmailApiKey());
    }

    @Test
    void getSenderEmailShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL))
                .thenReturn(Optional.empty());
        assertEquals("", service.getSenderEmail());
    }

    @Test
    void getRecipientEmailShouldReturnEmptyByDefault() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL))
                .thenReturn(Optional.empty());
        assertEquals("", service.getRecipientEmail());
    }
}
