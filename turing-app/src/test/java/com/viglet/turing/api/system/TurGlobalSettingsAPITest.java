package com.viglet.turing.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.system.TurEmailProviderType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
class TurGlobalSettingsAPITest {

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;

    @InjectMocks
    private TurGlobalSettingsAPI turGlobalSettingsAPI;

    @Test
    void shouldReturnCurrentGlobalSettings() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.COMMA);
        when(turGlobalSettingsService.getPythonExecutable()).thenReturn("");
        when(turGlobalSettingsService.getDefaultLlmId()).thenReturn("");
        when(turGlobalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(turGlobalSettingsService.getEmailApiKey()).thenReturn("");
        when(turGlobalSettingsService.getSenderEmail()).thenReturn("noreply@example.com");
        when(turGlobalSettingsService.getSenderName()).thenReturn("Turing");
        when(turGlobalSettingsService.getRecipientEmail()).thenReturn("admin@example.com");

        TurGlobalSettingsBean result = turGlobalSettingsAPI.getGlobalSettings();

        assertThat(result.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.COMMA);
        assertThat(result.getEmailProvider()).isEqualTo(TurEmailProviderType.BREVO);
        assertThat(result.getSenderEmail()).isEqualTo("noreply@example.com");
        assertThat(result.getSenderName()).isEqualTo("Turing");
        assertThat(result.getRecipientEmail()).isEqualTo("admin@example.com");
        verify(turGlobalSettingsService).getDecimalSeparator();
        verify(turGlobalSettingsService).getEmailProvider();
    }

    @Test
    void shouldUpdateGlobalSettings() {
        TurGlobalSettingsBean payload = TurGlobalSettingsBean.builder()
                .decimalSeparator(TurGlobalDecimalSeparator.DOT)
                .emailProvider(TurEmailProviderType.BREVO)
                .emailApiKey("xkeysib-123")
                .senderEmail("noreply@example.com")
                .senderName("Turing")
                .recipientEmail("admin@example.com")
                .build();
        when(turGlobalSettingsService.updateDecimalSeparator(TurGlobalDecimalSeparator.DOT))
                .thenReturn(TurGlobalDecimalSeparator.DOT);
        when(turGlobalSettingsService.updatePythonExecutable(any())).thenReturn("");
        when(turGlobalSettingsService.updateDefaultLlmId(any())).thenReturn("");
        when(turGlobalSettingsService.updateEmailProvider(TurEmailProviderType.BREVO))
                .thenReturn(TurEmailProviderType.BREVO);
        when(turGlobalSettingsService.updateEmailApiKey("xkeysib-123")).thenReturn("xkeysib-123");
        when(turGlobalSettingsService.updateSenderEmail("noreply@example.com")).thenReturn("noreply@example.com");
        when(turGlobalSettingsService.updateSenderName("Turing")).thenReturn("Turing");
        when(turGlobalSettingsService.updateRecipientEmail("admin@example.com")).thenReturn("admin@example.com");

        TurGlobalSettingsBean result = turGlobalSettingsAPI.updateGlobalSettings(payload);

        assertThat(result.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
        assertThat(result.getEmailProvider()).isEqualTo(TurEmailProviderType.BREVO);
        assertThat(result.getEmailApiKey()).isEqualTo("xkeysib-123");
        assertThat(result.getSenderEmail()).isEqualTo("noreply@example.com");
        verify(turGlobalSettingsService).updateDecimalSeparator(TurGlobalDecimalSeparator.DOT);
        verify(turGlobalSettingsService).updateEmailProvider(TurEmailProviderType.BREVO);
    }
}
