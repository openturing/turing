package com.viglet.turing.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.system.TurGlobalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/system/global-settings")
@Tag(name = "Global Settings", description = "Global Settings API")
public class TurGlobalSettingsAPI {
    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurGlobalSettingsAPI(TurGlobalSettingsService turGlobalSettingsService) {
        this.turGlobalSettingsService = turGlobalSettingsService;
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
}
