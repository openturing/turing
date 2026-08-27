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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T1034 / §XX.19 — writes the source-computed checksum a connector already
 * publishes onto the indexed document.
 *
 * <p>{@link TurSNJobItem} has carried a {@code checksum} for a long time and
 * {@code TurLoggingUtils} copies it onto the indexing log, where it stopped: no
 * indexing path wrote it to a document, so the one piece of per-document
 * identity the source computed itself lived only in a log.
 *
 * <p>The index inventory (T1029) had to work around that, reporting a version
 * resolved from {@code modification_date} or {@code publication_date}. That is
 * weak in two ways an audit meets immediately: a source that stamps no date gets
 * no version at all, and a date rewritten without a content change reports drift
 * that is not there. A checksum has neither problem.
 *
 * <p>Defensive by construction, like the other index-time enrichers: a job item
 * with no checksum is byte-for-byte the legacy path — no field is provisioned
 * and nothing is written — and any provisioning failure is logged and swallowed
 * rather than blocking the document.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurSNChecksumIndexer {

    private final TurSNFieldProvisioner fieldProvisioner;

    /**
     * Attaches {@code checksum} to the attribute map when the job item carries
     * one, provisioning the field on first use.
     *
     * <p>Never throws: a checksum is an audit convenience, and failing to store
     * one must not cost the document its place in the index.
     */
    public void enrich(TurSNSite turSNSite, TurSNJobItem turSNJobItem,
            Map<String, Object> attributes) {
        if (turSNSite == null || turSNJobItem == null || attributes == null) {
            return;
        }
        String checksum = turSNJobItem.getChecksum();
        if (StringUtils.isBlank(checksum)) {
            // T381 grounding: absent is absent. A connector that computes no
            // checksum must not get an empty one indexed, or an audit reads
            // "every document has the same checksum" as "nothing changed".
            return;
        }
        try {
            ensureField(turSNSite);
            attributes.put(TurSNFieldName.CHECKSUM, checksum.trim());
        } catch (Exception e) {
            log.warn("[T1034] Could not index the checksum on site '{}': {}",
                    turSNSite.getName(), e.getMessage());
        }
    }

    private void ensureField(TurSNSite turSNSite) {
        fieldProvisioner.ensureField(turSNSite, TurSNJobAttributeSpec.builder()
                .name(TurSNFieldName.CHECKSUM)
                .type(TurSEFieldType.STRING)
                .description("T1034 source-computed content checksum, published by the "
                        + "connector on the job item. Reported by the index inventory so an "
                        + "external audit can compare content rather than a timestamp.")
                .mandatory(false)
                .multiValued(false)
                .facet(false)
                .build());
    }
}
