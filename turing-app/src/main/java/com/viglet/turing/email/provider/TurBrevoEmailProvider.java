package com.viglet.turing.email.provider;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.viglet.turing.email.TurEmailMessage;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class TurBrevoEmailProvider implements TurEmailProvider {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TurBrevoEmailProvider() {
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderType() {
        return "brevo";
    }

    @Override
    public void sendEmail(TurEmailMessage message, String apiKey) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of(
                        "name", message.getSenderName(),
                        "email", message.getSenderEmail()),
                "to", List.of(Map.of(
                        "email", message.getRecipientEmail(),
                        "name", message.getRecipientName() != null
                                ? message.getRecipientName()
                                : message.getRecipientEmail())),
                "subject", message.getSubject(),
                "htmlContent", message.getHtmlContent());

        try {
            String body = objectMapper.writeValueAsString(payload);

            restClient.post()
                    .uri(BREVO_API_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email sent via Brevo to {}", message.getRecipientEmail());
        } catch (Exception e) {
            log.error("Failed to send email via Brevo to {}", message.getRecipientEmail(), e);
            throw new IllegalStateException("Brevo email sending failed", e);
        }
    }
}
