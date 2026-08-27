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
package com.viglet.turing.sn.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.inventory.TurSNInventoryEntry;
import com.viglet.turing.client.sn.inventory.TurSNInventoryPage;
import com.viglet.turing.plugins.se.TurSEInventoryEntry;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1035 / §XX.20 — the SDK declares its own inventory records so a published
 * client drags nothing extra into a connector, and pays for that with drift. This
 * is where it is paid: what {@code TurSNSiteInventoryAPI} actually serialises is
 * deserialised into the records the SDK client reads, field for field.
 *
 * <p>A rename on either side fails here rather than at a customer's first audit —
 * and silently, since an unread field would arrive as {@code null} and read as
 * "this document has no checksum".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNInventorySdkWireContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    private static TurSNIndexInventoryResponse serverResponse() {
        return new TurSNIndexInventoryResponse("s1", "Catalog", "en-US",
                List.of("en-US", "pt-BR"), "aem",
                List.of(new TurSEInventoryEntry("doc-a", "Page", 1_594_310_196_000L,
                                "modification_date", "sha256:aaa"),
                        new TurSEInventoryEntry("doc-b", null, null, null, null)),
                "doc-b", true, 42L, true);
    }

    @Test
    @DisplayName("The endpoint's response deserialises into the SDK's page, field for field")
    void serverResponseBindsToTheSdkPage() {
        String payload = MAPPER.writeValueAsString(serverResponse());

        TurSNInventoryPage page = MAPPER.readValue(payload, TurSNInventoryPage.class);

        assertThat(page.siteId()).isEqualTo("s1");
        assertThat(page.siteName()).isEqualTo("Catalog");
        assertThat(page.locale()).isEqualTo("en-US");
        assertThat(page.locales()).containsExactly("en-US", "pt-BR");
        assertThat(page.source()).isEqualTo("aem");
        assertThat(page.nextCursor()).isEqualTo("doc-b");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.remaining()).isEqualTo(42L);
        assertThat(page.supported()).isTrue();
    }

    @Test
    @DisplayName("Every component of an entry survives the wire — the checksum most of all")
    void entryComponentsSurviveTheWire() {
        String payload = MAPPER.writeValueAsString(serverResponse());

        TurSNInventoryPage page = MAPPER.readValue(payload, TurSNInventoryPage.class);

        TurSNInventoryEntry first = page.entries().get(0);
        assertThat(first.id()).isEqualTo("doc-a");
        assertThat(first.type()).isEqualTo("Page");
        assertThat(first.version()).isEqualTo(1_594_310_196_000L);
        assertThat(first.versionField()).isEqualTo("modification_date");
        assertThat(first.checksum()).isEqualTo("sha256:aaa");
    }

    @Test
    @DisplayName("A document with no identity at all arrives as nulls, not as zeros")
    void absentIdentityStaysAbsent() {
        String payload = MAPPER.writeValueAsString(serverResponse());

        TurSNInventoryPage page = MAPPER.readValue(payload, TurSNInventoryPage.class);

        TurSNInventoryEntry second = page.entries().get(1);
        assertThat(second.id()).isEqualTo("doc-b");
        assertThat(second.version()).isNull();
        assertThat(second.versionField()).isNull();
        assertThat(second.checksum()).isNull();
        // A zero version would compare as 1970 and read as stale against anything.
        assertThat(second.staleAgainst(1L)).isFalse();
    }

    @Test
    @DisplayName("An unsupported engine's response reaches the SDK as unsupported, not as empty")
    void unsupportedSurvivesTheWire() {
        var unsupported = new TurSNIndexInventoryResponse("s1", "Catalog", "en-US",
                List.of("en-US"), "aem", List.of(), null, false, -1L, false);

        TurSNInventoryPage page = MAPPER.readValue(MAPPER.writeValueAsString(unsupported),
                TurSNInventoryPage.class);

        assertThat(page.supported()).isFalse();
        assertThat(page.isEmptyIndex()).isFalse();
        assertThat(page.remaining()).isEqualTo(-1L);
    }
}
