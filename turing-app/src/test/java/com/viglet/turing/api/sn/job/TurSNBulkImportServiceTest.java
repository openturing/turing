/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.sn.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.api.sn.queue.TurSNProcessQueue;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;

/**
 * T807 / §LV.5 (Block BG) — deterministic coverage for the opt-in direct
 * bulk-index fast path: eligibility gating (feature flag + vectorless-only) and
 * bounded-chunk dispatch to the processor, bypassing JMS.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNBulkImportServiceTest {

    @Mock
    private TurSNProcessQueue turSNProcessQueue;
    @Mock
    private TurSNSiteRepositoryPort turSNSiteRepositoryPort;

    private TurSNBulkImportService service;

    @BeforeEach
    void setUp() {
        service = new TurSNBulkImportService(turSNProcessQueue, turSNSiteRepositoryPort);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 2);
    }

    private static TurSNJobItem create(String id) {
        Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put(TurSNFieldName.ID, id);
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("site1"), Locale.US, attrs);
    }

    private static TurSNJobItems items(int n) {
        TurSNJobItems jobItems = new TurSNJobItems();
        for (int i = 0; i < n; i++) {
            jobItems.add(create(String.valueOf(i)));
        }
        return jobItems;
    }

    @Test
    void notEligibleWhenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.isEligible(items(3))).isFalse();
    }

    @Test
    void notEligibleWhenEmpty() {
        assertThat(service.isEligible(new TurSNJobItems())).isFalse();
        assertThat(service.isEligible(null)).isFalse();
    }

    @Test
    void notEligibleWhenAnySiteHasRagEnabled() {
        when(turSNSiteRepositoryPort.hasRagEnabledForSiteName("site1")).thenReturn(true);
        assertThat(service.isEligible(items(2))).isFalse();
    }

    @Test
    void eligibleForVectorlessSite() {
        when(turSNSiteRepositoryPort.hasRagEnabledForSiteName("site1")).thenReturn(false);
        assertThat(service.isEligible(items(2))).isTrue();
    }

    @Test
    void importDirectDispatchesBoundedChunksToProcessor() {
        // 5 items / batch-size 2 → 3 chunks handed straight to the processor.
        int processed = service.importDirect(items(5));

        assertThat(processed).isEqualTo(5);
        verify(turSNProcessQueue, times(3)).processIndexingQueue(any());
    }

    @Test
    void importDirectIsPerChunkFailOpen() {
        doThrow(new RuntimeException("boom")).doNothing()
                .when(turSNProcessQueue).processIndexingQueue(any());

        int processed = service.importDirect(items(4)); // 2 chunks; first throws

        // Both chunks attempted; only the surviving chunk's items are counted.
        verify(turSNProcessQueue, times(2)).processIndexingQueue(any());
        assertThat(processed).isEqualTo(2);
    }

    @Test
    void importDirectSingleChunkWhenBatchSizeNonPositive() {
        ReflectionTestUtils.setField(service, "batchSize", 0);

        int processed = service.importDirect(items(5));

        assertThat(processed).isEqualTo(5);
        verify(turSNProcessQueue, times(1)).processIndexingQueue(any());
    }
}
