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

package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSNConstants.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNConstantsTest {

    @Test
    void testFileProtocolConstant() {
        assertThat(TurSNConstants.FILE_PROTOCOL).isEqualTo("file://");
    }

    @Test
    void testExportFileConstant() {
        assertThat(TurSNConstants.EXPORT_FILE).isEqualTo("export.json");
    }

    @Test
    void testIndexingQueueConstant() {
        assertThat(TurSNConstants.INDEXING_QUEUE).isEqualTo("indexing.queue");
    }

    @Test
    void testIndexingQueueListenerConstant() {
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).isEqualTo("indexingQueueListener");
    }

    @Test
    void testAllConstantsAreNotNull() {
        assertThat(TurSNConstants.FILE_PROTOCOL).isNotNull();
        assertThat(TurSNConstants.EXPORT_FILE).isNotNull();
        assertThat(TurSNConstants.INDEXING_QUEUE).isNotNull();
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).isNotNull();
    }

    @Test
    void testAllConstantsAreNotEmpty() {
        assertThat(TurSNConstants.FILE_PROTOCOL).isNotEmpty();
        assertThat(TurSNConstants.EXPORT_FILE).isNotEmpty();
        assertThat(TurSNConstants.INDEXING_QUEUE).isNotEmpty();
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).isNotEmpty();
    }

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        Constructor<TurSNConstants> constructor = TurSNConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Semantic Navigation Constants class");
    }

    @Test
    void testConstantValues() {
        assertThat(TurSNConstants.FILE_PROTOCOL).startsWith("file:");
        assertThat(TurSNConstants.EXPORT_FILE).endsWith(".json");
        assertThat(TurSNConstants.INDEXING_QUEUE).contains("queue");
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).contains("Listener");
    }
}
