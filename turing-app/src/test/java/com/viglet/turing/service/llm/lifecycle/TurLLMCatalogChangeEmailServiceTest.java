/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.lifecycle;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.email.TurEmailService;
import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService.ChangeNotification;
import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService.ChangeType;

/**
 * T788 / §LIII.4 — unit tests for the opt-in change-feed email channel.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMCatalogChangeEmailServiceTest {

    @Mock
    private TurLLMCatalogChangeService changeService;
    @Mock
    private TurEmailService emailService;

    private TurLLMCatalogChangeEmailService service(boolean enabled) {
        TurLLMCatalogChangeEmailService s = new TurLLMCatalogChangeEmailService(changeService, emailService);
        ReflectionTestUtils.setField(s, "emailEnabled", enabled);
        return s;
    }

    private static ChangeNotification removed(String vendor, String model) {
        return new ChangeNotification(ChangeType.REMOVED, vendor, model, model, null, List.of());
    }

    @Test
    void doesNothingWhenDisabled() {
        service(false).emailChangeFeed();
        verify(emailService, never()).sendEmail(anyString(), anyString());
    }

    @Test
    void doesNothingWhenNoNotifications() {
        when(changeService.notificationsForUsedModels()).thenReturn(List.of());
        service(true).emailChangeFeed();
        verify(emailService, never()).sendEmail(anyString(), anyString());
    }

    @Test
    void emailsWhenNotificationsPresentThenDedupes() {
        when(changeService.notificationsForUsedModels())
                .thenReturn(List.of(removed("openai", "gpt-old")));
        TurLLMCatalogChangeEmailService s = service(true);

        s.emailChangeFeed();
        s.emailChangeFeed(); // same set → deduped

        // Sent exactly once; the HTML mentions the affected model.
        verify(emailService, times(1)).sendEmail(anyString(), contains("gpt-old"));
    }

    @Test
    void emailsAgainWhenTheSetChanges() {
        when(changeService.notificationsForUsedModels())
                .thenReturn(List.of(removed("openai", "gpt-old")))
                .thenReturn(List.of(removed("openai", "gpt-old"), removed("anthropic", "claude-old")));
        TurLLMCatalogChangeEmailService s = service(true);

        s.emailChangeFeed(); // first set
        s.emailChangeFeed(); // changed set → new email

        verify(emailService, times(2)).sendEmail(anyString(), anyString());
    }

    @Test
    void doesNotUpdateSignatureWhenSendThrows() {
        when(changeService.notificationsForUsedModels())
                .thenReturn(List.of(removed("openai", "gpt-old")));
        lenient().doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendEmail(anyString(), anyString());

        TurLLMCatalogChangeEmailService s = service(true);
        s.emailChangeFeed();
        s.emailChangeFeed(); // failed last time → retried, not deduped away

        verify(emailService, times(2)).sendEmail(anyString(), anyString());
    }
}
