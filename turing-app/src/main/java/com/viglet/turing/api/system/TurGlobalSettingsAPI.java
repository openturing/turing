package com.viglet.turing.api.system;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.email.TurEmailService;
import com.viglet.turing.system.TurGlobalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/system/global-settings")
@Tag(name = "Global Settings", description = "Global Settings API")
public class TurGlobalSettingsAPI {
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurEmailService turEmailService;

    public TurGlobalSettingsAPI(TurGlobalSettingsService turGlobalSettingsService,
            TurEmailService turEmailService) {
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turEmailService = turEmailService;
    }

    @Operation(summary = "Get global settings")
    @GetMapping
    public TurGlobalSettingsBean getGlobalSettings() {
        return TurGlobalSettingsBean.builder()
                .decimalSeparator(turGlobalSettingsService.getDecimalSeparator())
                .pythonExecutable(turGlobalSettingsService.getPythonExecutable())
                .defaultLlmId(turGlobalSettingsService.getDefaultLlmId())
                .emailProvider(turGlobalSettingsService.getEmailProvider())
                .emailApiKey(turGlobalSettingsService.getEmailApiKey())
                .senderEmail(turGlobalSettingsService.getSenderEmail())
                .senderName(turGlobalSettingsService.getSenderName())
                .recipientEmail(turGlobalSettingsService.getRecipientEmail())
                .build();
    }

    @Operation(summary = "Update global settings")
    @PutMapping
    public TurGlobalSettingsBean updateGlobalSettings(@RequestBody TurGlobalSettingsBean globalSettings) {
        return TurGlobalSettingsBean.builder()
                .decimalSeparator(turGlobalSettingsService
                        .updateDecimalSeparator(globalSettings.getDecimalSeparator()))
                .pythonExecutable(turGlobalSettingsService
                        .updatePythonExecutable(globalSettings.getPythonExecutable()))
                .defaultLlmId(turGlobalSettingsService
                        .updateDefaultLlmId(globalSettings.getDefaultLlmId()))
                .emailProvider(turGlobalSettingsService
                        .updateEmailProvider(globalSettings.getEmailProvider()))
                .emailApiKey(turGlobalSettingsService
                        .updateEmailApiKey(globalSettings.getEmailApiKey()))
                .senderEmail(turGlobalSettingsService
                        .updateSenderEmail(globalSettings.getSenderEmail()))
                .senderName(turGlobalSettingsService
                        .updateSenderName(globalSettings.getSenderName()))
                .recipientEmail(turGlobalSettingsService
                        .updateRecipientEmail(globalSettings.getRecipientEmail()))
                .build();
    }

    @Operation(summary = "Send a test email to validate email configuration")
    @PostMapping("/email/test")
    public ResponseEntity<TurEmailTestResponse> sendTestEmail() {
        try {
            turEmailService.sendEmail("Turing - Test Email",
                    "<div style=\"font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px;\">"
                    + "<h2 style=\"color:#6366f1;\">Email Configuration Test</h2>"
                    + "<p>This is a test email from <strong>Viglet Turing</strong>.</p>"
                    + "<p>If you received this message, your email settings are configured correctly.</p>"
                    + "<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:24px 0;\">"
                    + "<p style=\"font-size:12px;color:#94a3b8;\">Sent by Turing Enterprise Search Intelligence Platform</p>"
                    + "</div>");
            return ResponseEntity.ok(new TurEmailTestResponse(true, "Test email sent successfully."));
        } catch (Exception e) {
            log.error("Failed to send test email", e);
            return ResponseEntity.internalServerError()
                    .body(new TurEmailTestResponse(false, "Failed to send test email: " + e.getMessage()));
        }
    }

    public record TurEmailTestResponse(boolean success, String message) {
    }
}
