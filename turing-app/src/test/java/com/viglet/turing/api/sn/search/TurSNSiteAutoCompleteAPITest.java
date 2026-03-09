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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.sn.ac.TurSNAutoComplete;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteAutoCompleteAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteAutoCompleteAPITest {

    @Test
    void testAutoCompleteUsesRegularSearchWhenFiltersPresent() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteAutoCompleteAPI api = new TurSNSiteAutoCompleteAPI(turSNAutoComplete);
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("hello");
        params.setRows(5);
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> expected = List.of("one", "two");
        List<String> filterQueriesDefault = List.of("type:doc");

        when(turSNAutoComplete.autoCompleteWithRegularSearch("site", params, request))
                .thenReturn(expected);

        List<String> result = api.turSNSiteAutoComplete("site", params, filterQueriesDefault,
                null, null, TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, "en_US",
                request);

        assertThat(result).isEqualTo(expected);
        assertThat(params.getFq()).isEqualTo(filterQueriesDefault);
        verify(turSNAutoComplete).autoCompleteWithRegularSearch("site", params, request);
    }

    @Test
    void testAutoCompleteDefaultsRowsWhenNegativeAndNoFilters() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteAutoCompleteAPI api = new TurSNSiteAutoCompleteAPI(turSNAutoComplete);
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("hello");
        params.setRows(-1);
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> expected = List.of("one");

        when(turSNAutoComplete.autoComplete(eq("site"), eq("hello"), any(Locale.class), eq(20L)))
                .thenReturn(expected);

        List<String> result = api.turSNSiteAutoComplete("site", params, null, null, null,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, "en_US", request);

        assertThat(result).isEqualTo(expected);
        assertThat(params.getRows()).isEqualTo(20);
        verify(turSNAutoComplete).autoComplete(eq("site"), eq("hello"), any(Locale.class), eq(20L));
    }

    @Test
    void testAutoCompleteUsesLocaleAndRowsWhenNoFilters() {
        TurSNAutoComplete turSNAutoComplete = mock(TurSNAutoComplete.class);
        TurSNSiteAutoCompleteAPI api = new TurSNSiteAutoCompleteAPI(turSNAutoComplete);
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("world");
        params.setRows(7);
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<String> expected = List.of("world");

        when(turSNAutoComplete.autoComplete("site", "world", Locale.CANADA, 7L)).thenReturn(expected);

        List<String> result = api.turSNSiteAutoComplete("site", params, null, null, null,
                TurSNFilterQueryOperator.NONE, TurSNFilterQueryOperator.NONE, "en_CA", request);

        assertThat(result).isEqualTo(expected);
        assertThat(params.getLocale()).isEqualTo(Locale.CANADA);
    }
}
