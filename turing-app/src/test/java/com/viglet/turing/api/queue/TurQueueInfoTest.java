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

package com.viglet.turing.api.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurQueueInfo.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurQueueInfoTest {

    @Test
    void testNoArgsConstructor() {
        TurQueueInfo queueInfo = new TurQueueInfo();

        assertThat(queueInfo).isNotNull();
        assertThat(queueInfo.getName()).isNull();
        assertThat(queueInfo.getMessageCount()).isZero();
        assertThat(queueInfo.getConsumerCount()).isZero();
        assertThat(queueInfo.isPaused()).isFalse();
        assertThat(queueInfo.getStatus()).isNull();
        assertThat(queueInfo.isTemporary()).isFalse();
        assertThat(queueInfo.getAddress()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        TurQueueInfo queueInfo = new TurQueueInfo(
                "test.queue",
                100L,
                5L,
                false,
                "ACTIVE",
                false,
                "jms.queue.test");

        assertThat(queueInfo.getName()).isEqualTo("test.queue");
        assertThat(queueInfo.getMessageCount()).isEqualTo(100L);
        assertThat(queueInfo.getConsumerCount()).isEqualTo(5L);
        assertThat(queueInfo.isPaused()).isFalse();
        assertThat(queueInfo.getStatus()).isEqualTo("ACTIVE");
        assertThat(queueInfo.isTemporary()).isFalse();
        assertThat(queueInfo.getAddress()).isEqualTo("jms.queue.test");
    }

    @Test
    void testBuilder() {
        TurQueueInfo queueInfo = TurQueueInfo.builder()
                .name("indexing.queue")
                .messageCount(250L)
                .consumerCount(3L)
                .paused(true)
                .status("PAUSED")
                .temporary(true)
                .address("jms.queue.indexing")
                .build();

        assertThat(queueInfo.getName()).isEqualTo("indexing.queue");
        assertThat(queueInfo.getMessageCount()).isEqualTo(250L);
        assertThat(queueInfo.getConsumerCount()).isEqualTo(3L);
        assertThat(queueInfo.isPaused()).isTrue();
        assertThat(queueInfo.getStatus()).isEqualTo("PAUSED");
        assertThat(queueInfo.isTemporary()).isTrue();
        assertThat(queueInfo.getAddress()).isEqualTo("jms.queue.indexing");
    }

    @Test
    void testGettersAndSetters() {
        TurQueueInfo queueInfo = new TurQueueInfo();

        queueInfo.setName("my.queue");
        queueInfo.setMessageCount(42L);
        queueInfo.setConsumerCount(2L);
        queueInfo.setPaused(true);
        queueInfo.setStatus("RUNNING");
        queueInfo.setTemporary(false);
        queueInfo.setAddress("jms.queue.my");

        assertThat(queueInfo.getName()).isEqualTo("my.queue");
        assertThat(queueInfo.getMessageCount()).isEqualTo(42L);
        assertThat(queueInfo.getConsumerCount()).isEqualTo(2L);
        assertThat(queueInfo.isPaused()).isTrue();
        assertThat(queueInfo.getStatus()).isEqualTo("RUNNING");
        assertThat(queueInfo.isTemporary()).isFalse();
        assertThat(queueInfo.getAddress()).isEqualTo("jms.queue.my");
    }

    @Test
    void testEqualsAndHashCode() {
        TurQueueInfo queueInfo1 = TurQueueInfo.builder()
                .name("queue1")
                .messageCount(10L)
                .consumerCount(1L)
                .paused(false)
                .status("ACTIVE")
                .temporary(false)
                .address("jms.queue.1")
                .build();

        TurQueueInfo queueInfo2 = TurQueueInfo.builder()
                .name("queue1")
                .messageCount(10L)
                .consumerCount(1L)
                .paused(false)
                .status("ACTIVE")
                .temporary(false)
                .address("jms.queue.1")
                .build();

        TurQueueInfo queueInfo3 = TurQueueInfo.builder()
                .name("queue2")
                .messageCount(20L)
                .consumerCount(2L)
                .paused(true)
                .status("PAUSED")
                .temporary(true)
                .address("jms.queue.2")
                .build();

        assertThat(queueInfo1).isEqualTo(queueInfo2).isNotEqualTo(queueInfo3);
        assertThat(queueInfo1.hashCode()).hasSameHashCodeAs(queueInfo2.hashCode());
    }

    @Test
    void testToString() {
        TurQueueInfo queueInfo = TurQueueInfo.builder()
                .name("test.queue")
                .messageCount(5L)
                .consumerCount(1L)
                .paused(false)
                .status("ACTIVE")
                .temporary(false)
                .address("jms.queue.test")
                .build();

        String toString = queueInfo.toString();

        assertThat(toString).contains("test.queue").contains("5").contains("ACTIVE");
    }
}
