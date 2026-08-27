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
package com.viglet.turing.sn.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.viglet.turing.api.exception.TurUnsupportedEngineException;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * T1046 / §XX.23 — how a DSL query picks its executor.
 *
 * <p>The dispatch had no test at all, which is how the defect survived: it read
 * {@code executorMap.getOrDefault(engineType, executorMap.get("solr"))}, so a site
 * on an engine with no executor was answered by the <em>Solr</em> one. That is not
 * a degradation. {@code TurDslSolrExecutor.execute} builds a Solr client from the
 * site's own SE instance endpoint URL, so a Content AI site issued Solr queries
 * against Adobe's REST API, and because the executor catches {@code Exception} and
 * returns an empty {@code Optional}, the controller rendered a 404. A capability
 * gap arrived as "site not found", after a wrong request to a third party.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurDslSearchServiceDispatchTest {

    private static final String SITE = "atlas";
    private static final String LOCALE = "en_US";

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;
    @Mock
    private TurDslSearchEngineExecutor<Object, Object> solrExecutor;
    @Mock
    private TurDslSearchEngineExecutor<Object, Object> luceneExecutor;

    private TurDslSearchService service(String siteEngine) {
        TurSNSite site = new TurSNSite();
        site.setName(SITE);
        when(turSNSiteRepository.findByNameIgnoreCase(SITE)).thenReturn(Optional.of(site));
        when(pluginFactory.getPluginForSite(any())).thenReturn(plugin);
        when(plugin.getPluginType()).thenReturn(siteEngine);
        when(solrExecutor.getEngineType()).thenReturn("solr");
        when(luceneExecutor.getEngineType()).thenReturn("lucene");
        return new TurDslSearchService(turSNSiteRepository, pluginFactory,
                List.of(solrExecutor, luceneExecutor));
    }

    private static TurDslQueryRequest anyRequest() {
        return new TurDslQueryRequest(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("A site's own engine gets its own executor")
    void dispatchesToTheSitesEngine() {
        var service = service("lucene");
        when(luceneExecutor.execute(eq(SITE), any(Locale.class), any()))
                .thenReturn(Optional.of(new TurDslSearchResponse(1L, false, null, null)));

        assertThat(service.search(SITE, LOCALE, anyRequest())).isPresent();

        verify(luceneExecutor).execute(eq(SITE), any(Locale.class), any());
        verify(solrExecutor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("An engine with no executor is refused, not handed to Solr")
    void refusesAnEngineWithNoExecutor() {
        var service = service("contentai");

        assertThatThrownBy(() -> service.search(SITE, LOCALE, anyRequest()))
                .isInstanceOf(TurUnsupportedEngineException.class)
                .hasMessageContaining("contentai")
                .hasMessageContaining("DSL query API")
                // The caller's next question is which engine to use, so the
                // refusal answers it.
                .hasMessageContaining("lucene")
                .hasMessageContaining("solr");

        // The whole point: no executor ran. Before this, the Solr one did, and it
        // talked Solr to whatever endpoint the site's SE instance named.
        verify(solrExecutor, never()).execute(any(), any(), any());
        verify(luceneExecutor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("The refusal is 501 and carries the engine, so a client need not parse prose")
    void refusalIsNotImplementedAndNamesTheEngine() {
        var service = service("contentai");

        var thrown = org.assertj.core.api.Assertions
                .catchThrowableOfType(TurUnsupportedEngineException.class,
                        () -> service.search(SITE, LOCALE, anyRequest()));

        // 501, not 404 (the site exists) and not 400 (the request is fine). What
        // is missing is a server-side implementation for this engine.
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(thrown.getEngine()).isEqualTo("contentai");
        assertThat(thrown.getTypeUri()).endsWith("unsupported-engine");
    }

    @Test
    @DisplayName("An unknown site is still an empty Optional, not a refusal")
    void unknownSiteIsNotAnEngineProblem() {
        var service = service("solr");
        when(turSNSiteRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());

        // The engine check happens per site, so a site that does not exist must not
        // be reported as an unsupported engine.
        assertThat(service.search("nope", LOCALE, anyRequest())).isEmpty();
        verify(solrExecutor, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("The engine type is matched case-insensitively")
    void engineTypeIsLowercased() {
        var service = service("SOLR");
        when(solrExecutor.execute(eq(SITE), any(Locale.class), any()))
                .thenReturn(Optional.of(new TurDslSearchResponse(1L, false, null, null)));

        assertThat(service.search(SITE, LOCALE, anyRequest())).isPresent();
    }

    @Test
    @DisplayName("An executor that finds nothing still returns empty, not a refusal")
    void emptyResultIsNotARefusal() {
        var service = service("solr");
        when(solrExecutor.execute(eq(SITE), any(Locale.class), any()))
                .thenReturn(Optional.empty());

        // The two must stay distinguishable: this is the observation the old
        // fallback made indistinguishable from a wrong engine.
        assertThat(service.search(SITE, LOCALE, anyRequest())).isEmpty();
    }
}
