package com.viglet.turing.email;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.email.provider.TurEmailProvider;
import com.viglet.turing.email.provider.TurEmailProviderFactory;
import com.viglet.turing.system.TurEmailProviderType;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
class TurEmailServiceTest {

    @Mock
    private TurEmailProviderFactory emailProviderFactory;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @InjectMocks
    private TurEmailService emailService;

    @Test
    void sendEmailShouldCallProviderWhenSettingsComplete() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("api-key-123");
        when(globalSettingsService.getSenderEmail()).thenReturn("sender@test.com");
        when(globalSettingsService.getSenderName()).thenReturn("Sender");
        when(globalSettingsService.getRecipientEmail()).thenReturn("recipient@test.com");

        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(emailProviderFactory.getProvider("BREVO")).thenReturn(provider);

        emailService.sendEmail("Subject", "<p>Content</p>");

        verify(provider).sendEmail(any(TurEmailMessage.class), eq("api-key-123"));
    }

    @Test
    void sendEmailShouldSkipWhenApiKeyBlank() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("");
        when(globalSettingsService.getSenderEmail()).thenReturn("sender@test.com");
        when(globalSettingsService.getRecipientEmail()).thenReturn("recipient@test.com");

        emailService.sendEmail("Subject", "Content");

        verifyNoInteractions(emailProviderFactory);
    }

    @Test
    void sendEmailShouldSkipWhenSenderEmailBlank() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("key");
        when(globalSettingsService.getSenderEmail()).thenReturn("");
        when(globalSettingsService.getRecipientEmail()).thenReturn("recipient@test.com");

        emailService.sendEmail("Subject", "Content");

        verifyNoInteractions(emailProviderFactory);
    }

    @Test
    void sendEmailShouldSkipWhenRecipientEmailBlank() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("key");
        when(globalSettingsService.getSenderEmail()).thenReturn("sender@test.com");
        when(globalSettingsService.getRecipientEmail()).thenReturn("");

        emailService.sendEmail("Subject", "Content");

        verifyNoInteractions(emailProviderFactory);
    }

    @Test
    void sendEmailMessageShouldSkipWhenApiKeyBlank() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("");

        TurEmailMessage message = TurEmailMessage.builder()
                .senderEmail("s@test.com").recipientEmail("r@test.com")
                .subject("Test").htmlContent("Body").build();
        emailService.sendEmail(message);

        verifyNoInteractions(emailProviderFactory);
    }

    @Test
    void sendEmailShouldUseSenderEmailAsNameWhenNameBlank() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("key");
        when(globalSettingsService.getSenderEmail()).thenReturn("sender@test.com");
        when(globalSettingsService.getSenderName()).thenReturn("");
        when(globalSettingsService.getRecipientEmail()).thenReturn("recipient@test.com");

        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(emailProviderFactory.getProvider("BREVO")).thenReturn(provider);

        emailService.sendEmail("Subject", "Content");

        verify(provider).sendEmail(argThat(msg ->
                msg.getSenderName().equals("sender@test.com")), eq("key"));
    }
}
