/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteSpellCheckAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpellCheckAPITest {

    @Test
    void testSpellCheckReturnsNullWhenInstanceMissing() {
        TurSolr turSolr = mock(TurSolr.class);
        TurSolrInstanceProcess instanceProcess = mock(TurSolrInstanceProcess.class);
        TurSNSiteSpellCheckAPI api = new TurSNSiteSpellCheckAPI(turSolr, instanceProcess);

        when(instanceProcess.initSolrInstance("site", Locale.US)).thenReturn(Optional.empty());

        TurSNSiteSpellCheckBean result = api.turSNSiteSpellCheck("site", "en_US", "helo",
                mock(HttpServletRequest.class));

        assertThat(result).isNull();
        verifyNoInteractions(turSolr);
    }

    @Test
    void testSpellCheckBuildsResponseWhenInstanceExists() {
        TurSolr turSolr = mock(TurSolr.class);
        TurSolrInstanceProcess instanceProcess = mock(TurSolrInstanceProcess.class);
        TurSNSiteSpellCheckAPI api = new TurSNSiteSpellCheckAPI(turSolr, instanceProcess);
        TurSolrInstance instance = mock(TurSolrInstance.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(instanceProcess.initSolrInstance("site", Locale.US)).thenReturn(Optional.of(instance));
        TurSESpellCheckResult spellCheckResult = new TurSESpellCheckResult(true, "hello");
        spellCheckResult.setUsingCorrected(true);
        when(turSolr.spellCheckTerm(instance, "helo")).thenReturn(spellCheckResult);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://example.com/api"));
        when(request.getQueryString()).thenReturn("q=helo");

        TurSNSiteSpellCheckBean result = api.turSNSiteSpellCheck("site", "en_US", "helo", request);

        assertThat(result).isNotNull();
        assertThat(result.isCorrectedText()).isTrue();
        assertThat(result.isUsingCorrectedText()).isTrue();
        assertThat(result.getCorrected().getText()).isEqualTo("hello");
        assertThat(result.getOriginal().getText()).isEqualTo("helo");
    }

    @Test
    void testSpellCheckKeepsOriginalWhenNotCorrected() {
        TurSolr turSolr = mock(TurSolr.class);
        TurSolrInstanceProcess instanceProcess = mock(TurSolrInstanceProcess.class);
        TurSNSiteSpellCheckAPI api = new TurSNSiteSpellCheckAPI(turSolr, instanceProcess);
        TurSolrInstance instance = mock(TurSolrInstance.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(instanceProcess.initSolrInstance("site", Locale.US)).thenReturn(Optional.of(instance));
        TurSESpellCheckResult spellCheckResult = new TurSESpellCheckResult(false, "");
        when(turSolr.spellCheckTerm(instance, "query")).thenReturn(spellCheckResult);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://example.com/api"));
        when(request.getQueryString()).thenReturn("q=query");

        TurSNSiteSpellCheckBean result = api.turSNSiteSpellCheck("site", "en_US", "query", request);

        assertThat(result).isNotNull();
        assertThat(result.isCorrectedText()).isFalse();
        assertThat(result.getOriginal().getText()).isEqualTo("query");
    }
}
