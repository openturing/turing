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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsTemplate;

import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;

/**
 * Unit tests for TurSNQueue.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNQueueTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private Session session;

    @Mock
    private QueueBrowser browser;

    @Test
    void testGetQueueSizeCountsMessages() throws Exception {
        when(browser.getEnumeration()).thenReturn(Collections.enumeration(List.of("a", "b", "c")));
        when(jmsTemplate.browse(eq(TurSNConstants.INDEXING_QUEUE),
                ArgumentMatchers.<BrowserCallback<Integer>>any()))
                .thenAnswer(invocation -> {
                    BrowserCallback<Integer> callback = invocation.getArgument(1);
                    return callback.doInJms(session, browser);
                });

        TurSNQueue queue = new TurSNQueue(jmsTemplate);

        assertThat(queue.getQueueSize()).isEqualTo(3);
    }
}
