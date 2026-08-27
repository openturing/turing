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
package com.viglet.turing.sn.checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

/**
 * T1034 / §XX.19 — the checksum a connector already computes now reaches the
 * document. The behaviours worth pinning are the two absences: no checksum must
 * leave the indexing path byte-for-byte as it was, and a provisioning failure
 * must never cost the document its place in the index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNChecksumIndexerTest {

    @Mock
    private TurSNFieldProvisioner fieldProvisioner;

    private TurSNChecksumIndexer indexer;
    private TurSNSite site;

    @BeforeEach
    void setUp() {
        indexer = new TurSNChecksumIndexer(fieldProvisioner);
        site = new TurSNSite();
        site.setId("site-1");
        site.setName("Sample");
    }

    private static TurSNJobItem jobItem(String checksum) {
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"),
                Locale.US, new HashMap<>(), List.of(), checksum);
        return item;
    }

    private static Map<String, Object> attributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.ID, "doc-1");
        return attributes;
    }

    @Test
    @DisplayName("A published checksum is written onto the document")
    void checksumIsIndexed() {
        Map<String, Object> attributes = attributes();

        indexer.enrich(site, jobItem("abc123"), attributes);

        assertThat(attributes).containsEntry(TurSNFieldName.CHECKSUM, "abc123");
    }

    @Test
    @DisplayName("The field is provisioned as a single-valued, non-facet string")
    void fieldIsProvisionedOnFirstUse() {
        indexer.enrich(site, jobItem("abc123"), attributes());

        ArgumentCaptor<TurSNJobAttributeSpec> spec =
                ArgumentCaptor.forClass(TurSNJobAttributeSpec.class);
        verify(fieldProvisioner).ensureField(eq(site), spec.capture());
        assertThat(spec.getValue().getName()).isEqualTo(TurSNFieldName.CHECKSUM);
        assertThat(spec.getValue().getType()).isEqualTo(TurSEFieldType.STRING);
        assertThat(spec.getValue().isMultiValued()).isFalse();
        assertThat(spec.getValue().isFacet()).isFalse();
        assertThat(spec.getValue().isMandatory()).isFalse();
    }

    @Test
    @DisplayName("A checksum with surrounding whitespace is trimmed")
    void checksumIsTrimmed() {
        Map<String, Object> attributes = attributes();

        indexer.enrich(site, jobItem("  abc123\n"), attributes);

        assertThat(attributes).containsEntry(TurSNFieldName.CHECKSUM, "abc123");
    }

    @Test
    @DisplayName("No checksum provisions no field and writes nothing — the legacy path exactly")
    void noChecksumIsTheLegacyPath() {
        for (String absent : new String[] {null, "", "   "}) {
            Map<String, Object> attributes = attributes();

            indexer.enrich(site, jobItem(absent), attributes);

            assertThat(attributes).doesNotContainKey(TurSNFieldName.CHECKSUM);
        }
        verify(fieldProvisioner, never()).ensureField(any(), any());
    }

    @Test
    @DisplayName("A blank checksum is never indexed as the empty value")
    void blankChecksumIsNotIndexedAsEmpty() {
        // An empty checksum on every document would read to an audit as
        // "nothing ever changes", which is worse than reporting none.
        Map<String, Object> attributes = attributes();

        indexer.enrich(site, jobItem("   "), attributes);

        assertThat(attributes).doesNotContainValue("");
        assertThat(attributes).doesNotContainKey(TurSNFieldName.CHECKSUM);
    }

    @Test
    @DisplayName("A provisioning failure is swallowed, so the document still gets indexed")
    void provisioningFailureNeverBlocksTheDocument() {
        doThrow(new IllegalStateException("schema unreachable"))
                .when(fieldProvisioner).ensureField(any(), any());
        Map<String, Object> attributes = attributes();

        indexer.enrich(site, jobItem("abc123"), attributes);

        // The document keeps its other attributes and no exception escapes.
        assertThat(attributes).containsEntry(TurSNFieldName.ID, "doc-1");
        assertThat(attributes).doesNotContainKey(TurSNFieldName.CHECKSUM);
    }

    @Test
    @DisplayName("Null arguments are ignored rather than thrown on")
    void nullArgumentsAreIgnored() {
        indexer.enrich(null, jobItem("abc123"), attributes());
        indexer.enrich(site, null, attributes());
        indexer.enrich(site, jobItem("abc123"), null);

        verify(fieldProvisioner, never()).ensureField(any(), any());
    }
}
