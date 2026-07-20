/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.gateway.TurGatewayKeyService.GeneratedKey;
import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.repository.gateway.TurGatewayKeyRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T741 / §XLIX — unit coverage for virtual-key minting, authentication and
 * model-scope enforcement.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayKeyServiceTest {

    @Mock
    private TurGatewayKeyRepository repository;
    @Mock
    private TurSecretCryptoService secretCryptoService;

    private TurGatewayKeyService service() {
        return new TurGatewayKeyService(repository, secretCryptoService);
    }

    @Test
    void createMintsPrefixedKeyAndStoresHashNotRaw() {
        when(secretCryptoService.encrypt(anyString())).thenReturn("ENC");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        GeneratedKey gk = service().create("Marketing dev key", "gpt-4o", null, "tenant-1");

        assertThat(gk.rawKey()).startsWith(TurGatewayKeyService.KEY_PREFIX);
        assertThat(gk.key().getKeyPrefix()).startsWith(TurGatewayKeyService.KEY_PREFIX);
        assertThat(gk.key().getKeyPrefix()).doesNotContain(gk.rawKey().substring(20));
        assertThat(gk.key().getKeyHash()).hasSize(64);
        assertThat(gk.key().getSecretEncrypted()).isEqualTo("ENC");
        assertThat(gk.key().getTenantId()).isEqualTo("tenant-1");
        assertThat(gk.key().getEnabled()).isEqualTo(1);
    }

    @Test
    void authenticateMatchesMintedKeyByHash() {
        when(secretCryptoService.encrypt(anyString())).thenReturn("ENC");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        GeneratedKey gk = service().create("k", null, null, null);
        when(repository.findByKeyHash(gk.key().getKeyHash())).thenReturn(Optional.of(gk.key()));

        assertThat(service().authenticate(gk.rawKey())).containsSame(gk.key());
    }

    @Test
    void authenticateRejectsForeignPrefixWithoutHittingDb() {
        assertThat(service().authenticate("sk-openai-abc")).isEmpty();
        verify(repository, never()).findByKeyHash(anyString());
    }

    @Test
    void authenticateRejectsDisabledKey() {
        when(secretCryptoService.encrypt(anyString())).thenReturn("ENC");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        GeneratedKey gk = service().create("k", null, null, null);
        gk.key().setEnabled(0);
        when(repository.findByKeyHash(gk.key().getKeyHash())).thenReturn(Optional.of(gk.key()));

        assertThat(service().authenticate(gk.rawKey())).isEmpty();
    }

    @Test
    void authenticateRejectsExpiredKey() {
        when(secretCryptoService.encrypt(anyString())).thenReturn("ENC");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        GeneratedKey gk = service().create("k", null, LocalDateTime.now().minusDays(1), null);
        lenient().when(repository.findByKeyHash(gk.key().getKeyHash())).thenReturn(Optional.of(gk.key()));

        assertThat(service().authenticate(gk.rawKey())).isEmpty();
    }

    @Test
    void modelScopeAllowsAnyWhenBlank() {
        TurGatewayKey key = new TurGatewayKey();
        key.setAllowedModels(null);
        assertThat(service().isModelAllowed(key, "anything")).isTrue();
    }

    @Test
    void modelScopeMatchesCaseInsensitively() {
        TurGatewayKey key = new TurGatewayKey();
        key.setAllowedModels("gpt-4o, claude-3-5");
        assertThat(service().isModelAllowed(key, "GPT-4O")).isTrue();
        assertThat(service().isModelAllowed(key, "llama-3")).isFalse();
    }

    @Test
    void modelScopeRejectsNullModelUnderRestriction() {
        TurGatewayKey key = new TurGatewayKey();
        key.setAllowedModels("gpt-4o");
        assertThat(service().isModelAllowed(key, null)).isFalse();
    }
}
