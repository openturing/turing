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
package com.viglet.turing.sn.contentfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.persona.readability.TurReadabilityScorer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

/**
 * Unit tests for {@link TurSNContentFitIndexer} — the index-time audience
 * content-fit signal (T472). Uses the real (pure) readability scorer and mocks
 * only the repository and field provisioner.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNContentFitIndexerTest {

    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurSNFieldProvisioner fieldProvisioner;

    private TurSNContentFitIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new TurSNContentFitIndexer(new TurReadabilityScorer(), personaRepository,
                fieldProvisioner);
    }

    private TurSNSite siteWith(boolean enabled, String personaId) {
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setContentFitIndexingEnabled(enabled);
        genAi.setContentFitPersonaId(personaId);
        site.setTurSNSiteGenAi(genAi);
        return site;
    }

    private TurPersona audiencePersona() {
        TurPersona persona = new TurPersona();
        persona.setId("p1");
        persona.setName("Elementary Reader");
        persona.setPersonaKind(TurPersonaKind.AUDIENCE);
        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(TurPersonaReadingLevel.ELEMENTARY);
        audience.setPrimaryLanguage("en");
        persona.setAudience(audience);
        return persona;
    }

    private Map<String, Object> docWithText() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("title", "A simple title");
        attributes.put("text", "The cat sat on the mat. The dog ran fast. We had fun today.");
        return attributes;
    }

    @Test
    void scoresAndStoresFieldWhenEnabledWithAudiencePersona() {
        when(personaRepository.findById("p1")).thenReturn(Optional.of(audiencePersona()));
        Map<String, Object> attributes = docWithText();

        indexer.enrich(siteWith(true, "p1"), attributes);

        assertThat(attributes).containsKey(TurSNContentFitIndexer.FIELD_NAME);
        Object score = attributes.get(TurSNContentFitIndexer.FIELD_NAME);
        assertThat(score).isInstanceOf(Integer.class);
        assertThat((Integer) score).isBetween(0, 100);
        verify(fieldProvisioner).ensureField(any(), any());
    }

    @Test
    void noOpWhenDisabled() {
        Map<String, Object> attributes = docWithText();

        indexer.enrich(siteWith(false, "p1"), attributes);

        assertThat(attributes).doesNotContainKey(TurSNContentFitIndexer.FIELD_NAME);
        verify(personaRepository, never()).findById(any());
        verify(fieldProvisioner, never()).ensureField(any(), any());
    }

    @Test
    void noOpWhenNoPersonaConfigured() {
        Map<String, Object> attributes = docWithText();

        indexer.enrich(siteWith(true, "  "), attributes);

        assertThat(attributes).doesNotContainKey(TurSNContentFitIndexer.FIELD_NAME);
        verify(fieldProvisioner, never()).ensureField(any(), any());
    }

    @Test
    void noOpWhenPersonaIsNotAnAudience() {
        TurPersona speaker = new TurPersona();
        speaker.setId("p1");
        speaker.setPersonaKind(TurPersonaKind.SPEAKER);
        when(personaRepository.findById("p1")).thenReturn(Optional.of(speaker));
        Map<String, Object> attributes = docWithText();

        indexer.enrich(siteWith(true, "p1"), attributes);

        assertThat(attributes).doesNotContainKey(TurSNContentFitIndexer.FIELD_NAME);
        verify(fieldProvisioner, never()).ensureField(any(), any());
    }

    @Test
    void leavesBlankTextDocumentUnscored() {
        when(personaRepository.findById("p1")).thenReturn(Optional.of(audiencePersona()));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "doc-1"); // no title/text/abstract

        indexer.enrich(siteWith(true, "p1"), attributes);

        assertThat(attributes).doesNotContainKey(TurSNContentFitIndexer.FIELD_NAME);
        verify(fieldProvisioner, never()).ensureField(any(), any());
    }

    @Test
    void neverThrowsWhenGenAiIsNull() {
        TurSNSite site = new TurSNSite();
        site.setName("no-genai");
        Map<String, Object> attributes = docWithText();

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNContentFitIndexer.FIELD_NAME);
    }
}
