/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.persistence.dto.mcp.TurMcpServerDto;
import com.viglet.turing.persistence.mapper.mcp.TurMcpServerMapper;
import com.viglet.turing.persistence.mapper.mcp.TurMcpServerMapperImpl;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerType;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.tenant.TurInfraTenantScope;

/**
 * T1032 / §D.12 — the credential's write semantics. Three of these are the whole
 * reason the endpoint does not simply map the field: an admin editing the title
 * of an authenticated server must not silently erase its token, and a blank text
 * field is what a write-only secret always looks like.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurMcpServerAPIAuthTokenTest {

    @Mock
    private TurMcpServerRepository turMcpServerRepository;
    @Mock
    private TurAIAgentRepository turAIAgentRepository;
    @Mock
    private TurPersonaRepository turPersonaRepository;
    @Mock
    private TurInfraTenantScope tenantScope;
    @Mock
    private TurSecretCryptoService turSecretCryptoService;

    private TurMcpServerAPI api;

    @BeforeEach
    void setUp() {
        TurMcpServerMapper mapper = new TurMcpServerMapperImpl();
        api = new TurMcpServerAPI(turMcpServerRepository, mapper, turAIAgentRepository,
                turPersonaRepository, tenantScope, turSecretCryptoService);
        lenient().when(tenantScope.isVisibleToTenant(any())).thenReturn(true);
        lenient().when(turSecretCryptoService.encrypt(any()))
                .thenAnswer(call -> "enc(" + call.getArgument(0) + ")");
    }

    private TurMcpServer stored(String storedCipher) {
        TurMcpServer server = new TurMcpServer();
        server.setId("mcp-1");
        server.setTitle("Hosted");
        server.setType(TurMcpServerType.SYNC);
        server.setConnectionType(TurMcpServerConnectionType.HTTP);
        server.setUrl("https://mcp.example.com/mcp");
        server.setEnabled(1);
        server.setAuthTokenEncrypted(storedCipher);
        when(turMcpServerRepository.findById("mcp-1")).thenReturn(Optional.of(server));
        return server;
    }

    private static TurMcpServerDto payload(String authToken) {
        TurMcpServerDto dto = new TurMcpServerDto();
        dto.setId("mcp-1");
        dto.setTitle("Hosted renamed");
        dto.setType(TurMcpServerType.SYNC);
        dto.setConnectionType(TurMcpServerConnectionType.HTTP);
        dto.setUrl("https://mcp.example.com/mcp");
        dto.setEnabled(1);
        dto.setAuthToken(authToken);
        return dto;
    }

    @Test
    @DisplayName("A newly typed token is encrypted before it reaches the row")
    void newTokenIsEncrypted() {
        TurMcpServer server = stored(null);

        api.turMcpServerUpdate("mcp-1", payload("tok-123"));

        assertThat(server.getAuthTokenEncrypted()).isEqualTo("enc(tok-123)");
    }

    @Test
    @DisplayName("Editing another field with the token box left blank keeps the stored credential")
    void blankTokenPreservesTheStoredOne() {
        TurMcpServer server = stored("enc(tok-old)");

        api.turMcpServerUpdate("mcp-1", payload(""));
        assertThat(server.getAuthTokenEncrypted()).isEqualTo("enc(tok-old)");

        api.turMcpServerUpdate("mcp-1", payload(null));
        assertThat(server.getAuthTokenEncrypted()).isEqualTo("enc(tok-old)");
        assertThat(server.getTitle()).isEqualTo("Hosted renamed");
    }

    @Test
    @DisplayName("A new token replaces the stored one")
    void newTokenReplacesTheStoredOne() {
        TurMcpServer server = stored("enc(tok-old)");

        api.turMcpServerUpdate("mcp-1", payload("tok-new"));

        assertThat(server.getAuthTokenEncrypted()).isEqualTo("enc(tok-new)");
    }

    @Test
    @DisplayName("The clear sentinel is the only way back to an anonymous server")
    void clearSentinelRemovesTheToken() {
        TurMcpServer server = stored("enc(tok-old)");

        api.turMcpServerUpdate("mcp-1", payload(TurMcpServerAPI.AUTH_TOKEN_CLEAR));

        assertThat(server.getAuthTokenEncrypted()).isNull();
    }

    @Test
    @DisplayName("A token typed with whitespace is trimmed before encryption")
    void tokenIsTrimmed() {
        TurMcpServer server = stored(null);

        api.turMcpServerUpdate("mcp-1", payload("  tok-123  "));

        assertThat(server.getAuthTokenEncrypted()).isEqualTo("enc(tok-123)");
    }

    @Test
    @DisplayName("On create the plaintext never lands on the row, only the ciphertext")
    void createStoresOnlyCiphertext() {
        api.turMcpServerAdd(payload("tok-123"));

        var captor = org.mockito.ArgumentCaptor.forClass(TurMcpServer.class);
        org.mockito.Mockito.verify(turMcpServerRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthTokenEncrypted()).isEqualTo("enc(tok-123)");
        assertThat(captor.getValue().getAuthToken()).isNull();
    }

    @Test
    @DisplayName("A create that claims a ciphertext directly is ignored — only the plaintext path encrypts")
    void createIgnoresASubmittedCiphertext() {
        TurMcpServerDto dto = payload(null);
        dto.setAuthTokenEncrypted("enc(smuggled)");

        api.turMcpServerAdd(dto);

        var captor = org.mockito.ArgumentCaptor.forClass(TurMcpServer.class);
        org.mockito.Mockito.verify(turMcpServerRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthTokenEncrypted()).isNull();
    }

    @Test
    @DisplayName("The response says a credential is set without disclosing it")
    void responseReportsWhetherATokenIsSet() {
        stored("enc(tok-old)");

        TurMcpServerDto response = api.turMcpServerUpdate("mcp-1", payload(""));

        assertThat(response.isAuthTokenSet()).isTrue();
        assertThat(new TurMcpServerDto().isAuthTokenSet()).isFalse();
    }
}
