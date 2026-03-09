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

package com.viglet.turing.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.indexing.TurIndexingStatus;
import com.viglet.turing.commons.indexing.TurLoggingStatus;

/**
 * Unit tests for TurLoggingUtils.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurLoggingUtilsTest {

    private TurSNJobItem turSNJobItem;

    @BeforeEach
    void setUp() {
        turSNJobItem = new TurSNJobItem();
        turSNJobItem.setEnvironment("production");
        turSNJobItem.setLocale(java.util.Locale.US);
        turSNJobItem.setChecksum("checksum-abc");
        turSNJobItem.setSiteNames(List.of("site1", "site2"));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "test-id-123");
        attributes.put("url", "http://example.com/test");
        turSNJobItem.setAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        // Clean up any logging state if needed
    }

    @Test
    void testConstructorThrowsException() {
        assertThatThrownBy(this::instantiateTurLoggingUtilsConstructor)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Logging utility provider");
    }

    private void instantiateTurLoggingUtilsConstructor() throws Exception {
        var constructor = TurLoggingUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void testSetLoggingStatusWithAllParameters() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setLoggingStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    TurLoggingStatus.SUCCESS,
                    "Test details");
        });
    }

    @Test
    void testSetLoggingStatusWithNullDetails() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setLoggingStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    TurLoggingStatus.SUCCESS,
                    null);
        });
    }

    @Test
    void testSetLoggingStatusWithErrorStatus() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setLoggingStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    TurLoggingStatus.ERROR,
                    "Error occurred during indexing");
        });
    }

    @Test
    void testSetSuccessStatusWithIndex() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setSuccessStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetSuccessStatusWithDelete() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setSuccessStatus(
                    turSNJobItem,
                    TurIndexingStatus.DEINDEXED);
        });
    }

    @Test
    void testSetErrorStatusWithDetails() {
        String errorDetails = "Connection timeout while indexing";

        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setErrorStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    errorDetails);
        });
    }

    @Test
    void testSetErrorStatusWithNullDetails() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setErrorStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    null);
        });
    }

    @Test
    void testSetErrorStatusWithEmptyDetails() {
        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setErrorStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    "");
        });
    }

    @Test
    void testConstantsAreCorrect() {
        assertThat(TurLoggingUtils.URL).isEqualTo("url");
        assertThat(TurLoggingUtils.SERVER).isEqualTo("Server");
    }

    @Test
    void testSetLoggingStatusWithNoUrl() {
        TurSNJobItem itemWithoutUrl = new TurSNJobItem();
        itemWithoutUrl.setEnvironment("test");
        itemWithoutUrl.setLocale(java.util.Locale.ENGLISH);

        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setLoggingStatus(
                    itemWithoutUrl,
                    TurIndexingStatus.INDEXED,
                    TurLoggingStatus.SUCCESS,
                    null);
        });
    }

    @Test
    void testSetLoggingStatusWithMultipleSites() {
        turSNJobItem.setSiteNames(List.of("site1", "site2", "site3"));

        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setSuccessStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetLoggingStatusWithEmptySites() {
        turSNJobItem.setSiteNames(List.of());

        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setSuccessStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED);
        });
    }

    @Test
    void testSetLoggingStatusWithLongDetails() {
        String longDetails = "A".repeat(1000);

        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setErrorStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    longDetails);
        });
    }

    @Test
    void testSetLoggingStatusWithSpecialCharactersInDetails() {
        String specialDetails = "Error: <test> & \"quotes\" 'single' \n newline \t tab";

        assertThatNoException().isThrownBy(() -> {
            TurLoggingUtils.setErrorStatus(
                    turSNJobItem,
                    TurIndexingStatus.INDEXED,
                    specialDetails);
        });
    }
}
