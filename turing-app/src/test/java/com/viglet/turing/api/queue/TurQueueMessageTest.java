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

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurQueueMessage.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurQueueMessageTest {

    @Test
    void testNoArgsConstructor() {
        TurQueueMessage message = new TurQueueMessage();

        assertThat(message).isNotNull();
        assertThat(message.getMessageId()).isNull();
        assertThat(message.getContent()).isNull();
        assertThat(message.getTimestamp()).isNull();
        assertThat(message.getDeliveryCount()).isZero();
        assertThat(message.getType()).isNull();
        assertThat(message.getSize()).isZero();
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        TurQueueMessage message = new TurQueueMessage(
                "msg-123",
                "Test content",
                now,
                3,
                "INDEX",
                1024L);

        assertThat(message.getMessageId()).isEqualTo("msg-123");
        assertThat(message.getContent()).isEqualTo("Test content");
        assertThat(message.getTimestamp()).isEqualTo(now);
        assertThat(message.getDeliveryCount()).isEqualTo(3);
        assertThat(message.getType()).isEqualTo("INDEX");
        assertThat(message.getSize()).isEqualTo(1024L);
    }

    @Test
    void testBuilder() {
        LocalDateTime timestamp = LocalDateTime.of(2025, 1, 10, 10, 30, 0);
        TurQueueMessage message = TurQueueMessage.builder()
                .messageId("msg-456")
                .content("Document to index")
                .timestamp(timestamp)
                .deliveryCount(1)
                .type("INDEXING")
                .size(2048L)
                .build();

        assertThat(message.getMessageId()).isEqualTo("msg-456");
        assertThat(message.getContent()).isEqualTo("Document to index");
        assertThat(message.getTimestamp()).isEqualTo(timestamp);
        assertThat(message.getDeliveryCount()).isEqualTo(1);
        assertThat(message.getType()).isEqualTo("INDEXING");
        assertThat(message.getSize()).isEqualTo(2048L);
    }

    @Test
    void testGettersAndSetters() {
        TurQueueMessage message = new TurQueueMessage();
        LocalDateTime timestamp = LocalDateTime.now();

        message.setMessageId("msg-789");
        message.setContent("Test message content");
        message.setTimestamp(timestamp);
        message.setDeliveryCount(5);
        message.setType("DELETE");
        message.setSize(512L);

        assertThat(message.getMessageId()).isEqualTo("msg-789");
        assertThat(message.getContent()).isEqualTo("Test message content");
        assertThat(message.getTimestamp()).isEqualTo(timestamp);
        assertThat(message.getDeliveryCount()).isEqualTo(5);
        assertThat(message.getType()).isEqualTo("DELETE");
        assertThat(message.getSize()).isEqualTo(512L);
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDateTime timestamp = LocalDateTime.of(2025, 1, 10, 12, 0, 0);

        TurQueueMessage message1 = TurQueueMessage.builder()
                .messageId("msg-1")
                .content("Content 1")
                .timestamp(timestamp)
                .deliveryCount(1)
                .type("INDEX")
                .size(100L)
                .build();

        TurQueueMessage message2 = TurQueueMessage.builder()
                .messageId("msg-1")
                .content("Content 1")
                .timestamp(timestamp)
                .deliveryCount(1)
                .type("INDEX")
                .size(100L)
                .build();

        TurQueueMessage message3 = TurQueueMessage.builder()
                .messageId("msg-2")
                .content("Content 2")
                .timestamp(timestamp)
                .deliveryCount(2)
                .type("DELETE")
                .size(200L)
                .build();

        assertThat(message1)
                .isEqualTo(message2)
                .isNotEqualTo(message3)
                .hasSameHashCodeAs(message2);
    }

    @Test
    void testToString() {
        LocalDateTime timestamp = LocalDateTime.of(2025, 1, 10, 14, 30, 0);
        TurQueueMessage message = TurQueueMessage.builder()
                .messageId("msg-test")
                .content("Test content")
                .timestamp(timestamp)
                .deliveryCount(2)
                .type("UPDATE")
                .size(750L)
                .build();

        String toString = message.toString();

        assertThat(toString)
                .contains("msg-test")
                .contains("Test content")
                .contains("UPDATE");
    }

    @Test
    void testDeliveryCountIncrement() {
        TurQueueMessage message = new TurQueueMessage();

        message.setDeliveryCount(0);
        assertThat(message.getDeliveryCount()).isZero();

        message.setDeliveryCount(message.getDeliveryCount() + 1);
        assertThat(message.getDeliveryCount()).isEqualTo(1);

        message.setDeliveryCount(message.getDeliveryCount() + 1);
        assertThat(message.getDeliveryCount()).isEqualTo(2);
    }
}
