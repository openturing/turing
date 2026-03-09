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

package com.viglet.turing.sn.ac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.sn.search.TurSNSiteSearchCachedAPI;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.se.TurSEStopWord;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNAutoComplete.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNAutoCompleteTest {

        @Mock
        private TurSolr turSolr;

        @Mock
        private TurSEStopWord turSEStopWord;

        @Mock
        private TurSolrInstanceProcess turSolrInstanceProcess;

        @Mock
        private TurSNSiteSearchCachedAPI turSNSiteSearchCachedAPI;

        @Test
        void testAutoCompleteReturnsEmptyForShortQuery() {
                TurSNAutoComplete autoComplete = new TurSNAutoComplete(turSolr, turSEStopWord,
                                turSolrInstanceProcess, turSNSiteSearchCachedAPI);

                assertThat(autoComplete.autoComplete("site", "a", Locale.US, 10)).isEmpty();
        }

        @Test
        void testAutoCompleteReturnsEmptyWhenInstanceMissing() {
                TurSNAutoComplete autoComplete = new TurSNAutoComplete(turSolr, turSEStopWord,
                                turSolrInstanceProcess, turSNSiteSearchCachedAPI);
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US)).thenReturn(Optional.empty());

                assertThat(autoComplete.autoComplete("site", "ab", Locale.US, 10)).isEmpty();
        }

        @Test
        void testAutoCompleteWithRegularSearchExtractsTitles() {
                TurSNAutoComplete autoComplete = new TurSNAutoComplete(turSolr, turSEStopWord,
                                turSolrInstanceProcess, turSNSiteSearchCachedAPI);
                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api"));
                when(request.getQueryString()).thenReturn("q=hel");

                TurSNSiteSearchDocumentBean document = TurSNSiteSearchDocumentBean.builder()
                                .fields(Map.of(TurSNFieldName.TITLE, "Hello"))
                                .build();
                TurSNSiteSearchResultsBean results = new TurSNSiteSearchResultsBean();
                results.setDocument(List.of(document));
                TurSNSiteSearchBean bean = new TurSNSiteSearchBean();
                bean.setResults(results);

                when(turSNSiteSearchCachedAPI.searchCached(anyString(), any()))
                                .thenReturn(bean);

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("hel");
                params.setLocale(Locale.US);
                params.setP(1);
                params.setRows(10);

                List<String> output = autoComplete.autoCompleteWithRegularSearch("site", params, request);

                assertThat(output).containsExactly("Hello");
        }
}
