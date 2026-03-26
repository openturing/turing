package com.viglet.turing.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.email.provider.TurEmailProvider;
import com.viglet.turing.email.provider.TurEmailProviderFactory;
import com.viglet.turing.system.TurEmailProviderType;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Tests for TurEmailService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurEmailServiceTest {

    @Mock
    private TurEmailProviderFactory emailProviderFactory;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @InjectMocks
    private TurEmailService emailService;

    // --- sendEmail(String, String) ---

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

    @Test
    void sendEmailShouldBuildMessageWithCorrectFields() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("key");
        when(globalSettingsService.getSenderEmail()).thenReturn("from@test.com");
        when(globalSettingsService.getSenderName()).thenReturn("From Name");
        when(globalSettingsService.getRecipientEmail()).thenReturn("to@test.com");

        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(emailProviderFactory.getProvider("BREVO")).thenReturn(provider);

        emailService.sendEmail("My Subject", "<b>body</b>");

        verify(provider).sendEmail(argThat(msg ->
                "from@test.com".equals(msg.getSenderEmail()) &&
                "From Name".equals(msg.getSenderName()) &&
                "to@test.com".equals(msg.getRecipientEmail()) &&
                "My Subject".equals(msg.getSubject()) &&
                "<b>body</b>".equals(msg.getHtmlContent())), eq("key"));
    }

    // --- sendEmail(TurEmailMessage) ---

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
    void sendEmailMessageShouldCallProviderWhenApiKeyPresent() {
        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("valid-key");

        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(emailProviderFactory.getProvider("BREVO")).thenReturn(provider);

        TurEmailMessage message = TurEmailMessage.builder()
                .senderEmail("s@test.com").recipientEmail("r@test.com")
                .subject("Test").htmlContent("Body").build();
        emailService.sendEmail(message);

        verify(provider).sendEmail(message, "valid-key");
    }

    // --- sendTemplateEmail ---

    @Test
    void sendTemplateEmailShouldReplaceVariablesAndSend() {
        // The template loading uses ClassPathResource which requires actual classpath
        // resources. We can test the variable replacement logic indirectly by using
        // a spy approach. Since loadTemplate is a public method that loads from classpath,
        // we use a spy to stub only the template loading.
        TurEmailService spyService = spy(emailService);
        doReturn("Hello {{name}}, welcome to {{platform}}!")
                .when(spyService).loadTemplate("templates/test.html");

        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("key");
        when(globalSettingsService.getSenderEmail()).thenReturn("from@test.com");
        when(globalSettingsService.getSenderName()).thenReturn("Sender");
        when(globalSettingsService.getRecipientEmail()).thenReturn("to@test.com");

        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(emailProviderFactory.getProvider("BREVO")).thenReturn(provider);

        spyService.sendTemplateEmail("Welcome", "templates/test.html",
                Map.of("name", "Alice", "platform", "Turing"));

        verify(provider).sendEmail(argThat(msg ->
                "Hello Alice, welcome to Turing!".equals(msg.getHtmlContent())), eq("key"));
    }

    @Test
    void sendTemplateEmailShouldHandleEmptyVariables() {
        TurEmailService spyService = spy(emailService);
        doReturn("<p>Static content</p>")
                .when(spyService).loadTemplate("templates/static.html");

        when(globalSettingsService.getEmailProvider()).thenReturn(TurEmailProviderType.BREVO);
        when(globalSettingsService.getEmailApiKey()).thenReturn("key");
        when(globalSettingsService.getSenderEmail()).thenReturn("from@test.com");
        when(globalSettingsService.getSenderName()).thenReturn("Sender");
        when(globalSettingsService.getRecipientEmail()).thenReturn("to@test.com");

        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(emailProviderFactory.getProvider("BREVO")).thenReturn(provider);

        spyService.sendTemplateEmail("Test", "templates/static.html", Map.of());

        verify(provider).sendEmail(argThat(msg ->
                "<p>Static content</p>".equals(msg.getHtmlContent())), eq("key"));
    }

    // --- loadTemplate ---

    @Test
    void loadTemplateShouldThrowForMissingTemplate() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> emailService.loadTemplate("templates/nonexistent-xyz-12345.html"));
        assertTrue(ex.getMessage().contains("Email template not found"));
    }
}
