/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.gateway.TurGatewayAdminAPI.CreatedKey;
import com.viglet.turing.api.gateway.TurGatewayAdminAPI.KeyUpsertRequest;
import com.viglet.turing.api.gateway.TurGatewayAdminAPI.KeyView;
import com.viglet.turing.genai.gateway.TurGatewayKeyService;
import com.viglet.turing.genai.gateway.TurGatewayKeyService.GeneratedKey;
import com.viglet.turing.genai.gateway.TurGatewayUsageService;
import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.repository.gateway.TurGatewayKeyRepository;

/**
 * T748 / §XLIX — unit coverage for the gateway virtual-key CRUD admin surface,
 * focused on the security-critical contract: the raw key is returned exactly
 * once and the view never carries the secret.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayAdminAPITest {

    @Mock
    private TurGatewayUsageService usageService;
    @Mock
    private TurGatewayKeyService keyService;
    @Mock
    private TurGatewayKeyRepository keyRepository;
    @Mock
    private com.viglet.turing.genai.gateway.TurGatewayTrafficCaptureService trafficCapture;
    @Mock
    private com.viglet.turing.genai.distillation.TurOpenAiDistillationService distillationService;

    private TurGatewayAdminAPI api() {
        return new TurGatewayAdminAPI(usageService, keyService, keyRepository, trafficCapture,
                distillationService);
    }

    private TurGatewayKey key(String id) {
        TurGatewayKey k = new TurGatewayKey();
        k.setId(id);
        k.setName("dev key");
        k.setKeyPrefix("sk-turing-abc123••••");
        k.setKeyHash("hash");
        k.setSecretEncrypted("ENC");
        k.setEnabled(1);
        return k;
    }

    @Test
    void createReturnsRawKeyOnceAndPersistsBudget() {
        TurGatewayKey created = key("k-1");
        when(keyService.create(eq("dev key"), eq("gpt-4o"), isNull(), isNull()))
                .thenReturn(new GeneratedKey(created, "sk-turing-RAWSECRET"));
        when(keyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CreatedKey result = api().createKey(new KeyUpsertRequest("dev key", "gpt-4o", null,
                50.0, "cheap-llm", 100.0, 60, null));

        assertThat(result.rawKey()).isEqualTo("sk-turing-RAWSECRET");
        assertThat(created.getMonthlyBudgetUsd()).isEqualTo(50.0);
        assertThat(created.getHardMonthlyCapUsd()).isEqualTo(100.0);
        assertThat(created.getRateLimitPerMinute()).isEqualTo(60);
        verify(keyRepository).save(created);
    }

    @Test
    void keyViewNeverExposesSecret() {
        when(keyRepository.findAll()).thenReturn(List.of(key("k-1")));

        List<KeyView> views = api().listKeys();

        assertThat(views).hasSize(1);
        KeyView view = views.get(0);
        assertThat(view.keyPrefix()).isEqualTo("sk-turing-abc123••••");
        // KeyView is a fixed record with no secret/hash component — assert the
        // fields that exist are the safe ones.
        assertThat(view.id()).isEqualTo("k-1");
        assertThat(view.name()).isEqualTo("dev key");
    }

    @Test
    void rotateReturnsFreshRawKey() {
        TurGatewayKey existing = key("k-1");
        when(keyRepository.findById("k-1")).thenReturn(Optional.of(existing));
        when(keyService.rotate(existing)).thenReturn(new GeneratedKey(existing, "sk-turing-NEWSECRET"));

        CreatedKey result = api().rotateKey("k-1");

        assertThat(result.rawKey()).isEqualTo("sk-turing-NEWSECRET");
    }

    @Test
    void updateAppliesScopeAndBudget() {
        TurGatewayKey existing = key("k-1");
        when(keyRepository.findById("k-1")).thenReturn(Optional.of(existing));
        when(keyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        KeyView view = api().updateKey("k-1", new KeyUpsertRequest("renamed", "claude-3", null,
                null, null, null, 30, 0));

        assertThat(view.name()).isEqualTo("renamed");
        assertThat(view.allowedModels()).isEqualTo("claude-3");
        assertThat(view.enabled()).isZero();
        assertThat(view.rateLimitPerMinute()).isEqualTo(30);
    }
}
