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

package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.turing.api.sn.bean.TurSNSiteMetricsTopTermsBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

/**
 * Unit tests for TurSNSiteMetricsAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteMetricsAPITest {

    @Test
    void testGetLiveMetricsCreatesSixtyBucketsAndAggregatesHits() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        TurSNSiteMetricAccess metric1 = new TurSNSiteMetricAccess();
        metric1.setAccessDate(now.minusSeconds(20));
        TurSNSiteMetricAccess metric2 = new TurSNSiteMetricAccess();
        metric2.setAccessDate(now.minusSeconds(20));
        TurSNSiteMetricAccess metric3 = new TurSNSiteMetricAccess();
        metric3.setAccessDate(now.minusSeconds(5));

        when(metricRepository.findLastMinuteMetrics(org.mockito.ArgumentMatchers.eq("site"),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(metric1, metric2, metric3));

        List<Map<String, Object>> result = api.getLiveMetrics("site");

        assertThat(result)
                .hasSize(60)
                .allSatisfy(item -> assertThat(item).containsKeys("time", "displayTime", "accesses"))
                .allSatisfy(item -> assertThat(item.get("displayTime").toString()).hasSize(8))
                .anySatisfy(item -> assertThat(item).containsEntry("accesses", 2L))
                .anySatisfy(item -> assertThat(item).containsEntry("accesses", 1L));
    }

    @Test
    void testTopTermsTodayReturnsDefaultWhenMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsToday("site", 10);

        assertThat(result.getTopTerms()).isEmpty();
        assertThat(result.getTotalTermsPeriod()).isZero();
        assertThat(result.getTotalTermsPreviousPeriod()).isZero();
    }

    @Test
    void testTopTermsTodayReturnsMetricsWhenFound() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);
        TurSNSite site = new TurSNSite();
        TurSNSiteMetricAccessTerm term = mock(TurSNSiteMetricAccessTerm.class);

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(metricRepository.topTermsBetweenDates(org.mockito.ArgumentMatchers.eq(site),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(term));
        when(metricRepository.countTermsByPeriod(org.mockito.ArgumentMatchers.eq(site),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(12)
                .thenReturn(6);

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsToday("site", 10);

        assertThat(result.getTopTerms()).hasSize(1);
        assertThat(result.getTotalTermsPeriod()).isEqualTo(12);
        assertThat(result.getTotalTermsPreviousPeriod()).isEqualTo(6);
    }

    @Test
    void testTopTermsThisWeekReturnsDefaultWhenMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsThisWeek("site", 10);

        assertThat(result.getTopTerms()).isEmpty();
        assertThat(result.getTotalTermsPeriod()).isZero();
    }

    @Test
    void testTopTermsThisWeekReturnsMetricsWhenFound() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(metricRepository.topTermsBetweenDates(org.mockito.ArgumentMatchers.eq(site),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        when(metricRepository.countTermsByPeriod(org.mockito.ArgumentMatchers.eq(site),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(8)
                .thenReturn(4);

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsThisWeek("site", 10);

        assertThat(result.getTotalTermsPeriod()).isEqualTo(8);
        assertThat(result.getTotalTermsPreviousPeriod()).isEqualTo(4);
    }

    @Test
    void testTopTermsThisMonthReturnsDefaultWhenMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsThisMonth("site", 10);

        assertThat(result.getTopTerms()).isEmpty();
        assertThat(result.getTotalTermsPeriod()).isZero();
    }

    @Test
    void testTopTermsThisMonthReturnsMetricsWhenFound() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(metricRepository.topTermsBetweenDates(org.mockito.ArgumentMatchers.eq(site),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        when(metricRepository.countTermsByPeriod(org.mockito.ArgumentMatchers.eq(site),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(15)
                .thenReturn(10);

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsThisMonth("site", 10);

        assertThat(result.getTotalTermsPeriod()).isEqualTo(15);
        assertThat(result.getTotalTermsPreviousPeriod()).isEqualTo(10);
    }

    @Test
    void testTopTermsAllTimeReturnsDefaultWhenMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsAllTime("site", 10);

        assertThat(result.getTopTerms()).isEmpty();
        assertThat(result.getTotalTermsPeriod()).isZero();
    }

    @Test
    void testTopTermsAllTimeReturnsMetricsWhenFound() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteMetricAccessRepository metricRepository = mock(TurSNSiteMetricAccessRepository.class);
        TurSNSiteMetricsAPI api = new TurSNSiteMetricsAPI(siteRepository, metricRepository);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(metricRepository.topTerms(org.mockito.ArgumentMatchers.eq(site), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        when(metricRepository.countTerms(site)).thenReturn(5);

        TurSNSiteMetricsTopTermsBean result = api.turSNSiteMetricsTopTermsAllTime("site", 10);

        assertThat(result.getTotalTermsPeriod()).isEqualTo(5);
        verify(metricRepository).topTerms(org.mockito.ArgumentMatchers.eq(site), org.mockito.ArgumentMatchers.any());
    }
}
