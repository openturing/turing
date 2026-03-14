package com.viglet.turing.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.viglet.turing.email.provider.TurEmailProvider;
import com.viglet.turing.email.provider.TurEmailProviderFactory;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurEmailService {

    private final TurEmailProviderFactory emailProviderFactory;
    private final TurGlobalSettingsService globalSettingsService;

    public TurEmailService(TurEmailProviderFactory emailProviderFactory,
            TurGlobalSettingsService globalSettingsService) {
        this.emailProviderFactory = emailProviderFactory;
        this.globalSettingsService = globalSettingsService;
    }

    public void sendEmail(String subject, String htmlContent) {
        String providerType = globalSettingsService.getEmailProvider().name();
        String apiKey = globalSettingsService.getEmailApiKey();
        String senderEmail = globalSettingsService.getSenderEmail();
        String senderName = globalSettingsService.getSenderName();
        String recipientEmail = globalSettingsService.getRecipientEmail();

        if (apiKey.isBlank() || senderEmail.isBlank() || recipientEmail.isBlank()) {
            log.warn("Email settings incomplete — skipping email send. "
                    + "Configure API key, sender email, and recipient email in Global Settings.");
            return;
        }

        TurEmailMessage message = TurEmailMessage.builder()
                .senderEmail(senderEmail)
                .senderName(senderName.isBlank() ? senderEmail : senderName)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .htmlContent(htmlContent)
                .build();

        TurEmailProvider provider = emailProviderFactory.getProvider(providerType);
        provider.sendEmail(message, apiKey);
    }

    public void sendEmail(TurEmailMessage message) {
        String providerType = globalSettingsService.getEmailProvider().name();
        String apiKey = globalSettingsService.getEmailApiKey();

        if (apiKey.isBlank()) {
            log.warn("Email API key not configured — skipping email send.");
            return;
        }

        TurEmailProvider provider = emailProviderFactory.getProvider(providerType);
        provider.sendEmail(message, apiKey);
    }

    public void sendTemplateEmail(String subject, String templatePath, Map<String, String> variables) {
        String htmlContent = loadTemplate(templatePath);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            htmlContent = htmlContent.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        sendEmail(subject, htmlContent);
    }

    public String loadTemplate(String templatePath) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to load email template: {}", templatePath, e);
            throw new IllegalStateException("Email template not found: " + templatePath, e);
        }
    }
}
