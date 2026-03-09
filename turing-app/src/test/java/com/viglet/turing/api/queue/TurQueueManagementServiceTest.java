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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Unit tests for TurQueueManagementService.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurQueueManagementServiceTest {

        @Mock
        private MBeanServer mbeanServer;

        private TurQueueManagementService service;

        @BeforeEach
        void setUp() {
                service = new TurQueueManagementService();
                try {
                        var mbeanServerField = TurQueueManagementService.class.getDeclaredField("mbeanServer");
                        mbeanServerField.setAccessible(true);
                        mbeanServerField.set(service, mbeanServer);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }

        @Test
        void testGetAllQueuesWithMultipleQueues() throws Exception {
                ObjectName pattern = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue=*");

                ObjectName queue1Name = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue=queue1,address=addr1");
                ObjectName queue2Name = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue=queue2,address=addr2");

                ObjectInstance instance1 = new ObjectInstance(queue1Name,
                                "org.apache.activemq.artemis.core.server.impl.QueueImpl");
                ObjectInstance instance2 = new ObjectInstance(queue2Name,
                                "org.apache.activemq.artemis.core.server.impl.QueueImpl");

                Set<ObjectInstance> instances = new HashSet<>(Arrays.asList(instance1, instance2));

                when(mbeanServer.queryMBeans(eq(pattern), isNull())).thenReturn(instances);
                when(mbeanServer.getAttribute(queue1Name, "MessageCount")).thenReturn(10L);
                when(mbeanServer.getAttribute(queue1Name, "ConsumerCount")).thenReturn(2L);
                when(mbeanServer.getAttribute(queue1Name, "Paused")).thenReturn(false);
                when(mbeanServer.getAttribute(queue1Name, "Temporary")).thenReturn(false);

                when(mbeanServer.getAttribute(queue2Name, "MessageCount")).thenReturn(5L);
                when(mbeanServer.getAttribute(queue2Name, "ConsumerCount")).thenReturn(1L);
                when(mbeanServer.getAttribute(queue2Name, "Paused")).thenReturn(true);
                when(mbeanServer.getAttribute(queue2Name, "Temporary")).thenReturn(false);

                List<TurQueueInfo> queues = service.getAllQueues();

                assertThat(queues).hasSize(2);
                assertThat(queues).extracting(TurQueueInfo::getName).containsExactlyInAnyOrder("queue1", "queue2");

                TurQueueInfo queue1 = queues.stream().filter(q -> "queue1".equals(q.getName())).findFirst()
                                .orElseThrow();
                assertThat(queue1.getMessageCount()).isEqualTo(10L);
                assertThat(queue1.getConsumerCount()).isEqualTo(2L);
                assertThat(queue1.isPaused()).isFalse();
                assertThat(queue1.getStatus()).isEqualTo("ACTIVE");
                assertThat(queue1.getAddress()).isEqualTo("addr1");
        }

        @Test
        void testGetAllQueuesWithException() throws Exception {
                ObjectName pattern = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue=*");

                when(mbeanServer.queryMBeans(eq(pattern), isNull())).thenThrow(new RuntimeException("MBean error"));
                withLoggerOff(() -> {
                        List<TurQueueInfo> queues = service.getAllQueues();

                        assertThat(queues).isEmpty();
                });
        }

        @Test
        void testGetAllQueuesWithEmptyResult() throws Exception {
                ObjectName pattern = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue=*");

                when(mbeanServer.queryMBeans(eq(pattern), isNull())).thenReturn(Collections.emptySet());

                List<TurQueueInfo> queues = service.getAllQueues();

                assertThat(queues).isEmpty();
        }

        @Test
        void testGetQueueMessagesSuccess() throws Exception {
                String queueName = "testQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));

                Map<String, Object> message1 = new HashMap<>();
                message1.put("messageID", "msg-1");
                message1.put("text", "Test message 1");
                message1.put("timestamp", System.currentTimeMillis());
                message1.put("deliveryCount", 1);
                message1.put("type", "TEXT");
                message1.put("size", 100L);

                Object[] messages = new Object[] { message1 };

                when(mbeanServer.invoke(eq(queueObjectName), eq("browse"),
                                any(Object[].class), any(String[].class))).thenReturn(messages);

                List<TurQueueMessage> result = service.getQueueMessages(queueName, 10);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getMessageId()).isEqualTo("msg-1");
                assertThat(result.get(0).getContent()).isEqualTo("Test message 1");
                assertThat(result.get(0).getDeliveryCount()).isEqualTo(1);
                assertThat(result.get(0).getType()).isEqualTo("TEXT");
                assertThat(result.get(0).getSize()).isEqualTo(100L);
        }

        @Test
        void testGetQueueMessagesWithNonExistentQueue() {
                String queueName = "nonExistentQueue";

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.emptySet());

                List<TurQueueMessage> result = service.getQueueMessages(queueName, 10);

                assertThat(result).isEmpty();
        }

        @Test
        void testGetQueueMessagesWithException() throws Exception {
                String queueName = "errorQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));
                when(mbeanServer.invoke(eq(queueObjectName), eq("browse"),
                                any(Object[].class), any(String[].class)))
                                .thenThrow(new RuntimeException("Invoke error"));

                withLoggerOff(() -> {
                        List<TurQueueMessage> result = service.getQueueMessages(queueName, 10);

                        assertThat(result).isEmpty();
                });
        }

        @Test
        void testPauseQueueSuccess() throws Exception {
                String queueName = "testQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));
                when(mbeanServer.invoke(eq(queueObjectName), eq("pause"),
                                any(Object[].class), any(String[].class))).thenReturn(null);

                boolean result = service.pauseQueue(queueName);

                assertThat(result).isTrue();
                verify(mbeanServer).invoke(eq(queueObjectName), eq("pause"),
                                any(Object[].class), any(String[].class));
        }

        @Test
        void testPauseQueueWithNonExistentQueue() throws Exception {
                String queueName = "nonExistentQueue";

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.emptySet());

                boolean result = service.pauseQueue(queueName);

                assertThat(result).isFalse();
                verify(mbeanServer, never()).invoke(any(ObjectName.class), eq("pause"),
                                any(Object[].class), any(String[].class));
        }

        @Test
        void testResumeQueueSuccess() throws Exception {
                String queueName = "testQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));
                when(mbeanServer.invoke(eq(queueObjectName), eq("resume"),
                                any(Object[].class), any(String[].class))).thenReturn(null);

                withLoggerOff(() -> {
                        boolean result = service.resumeQueue(queueName);

                        assertThat(result).isTrue();
                        verify(mbeanServer).invoke(eq(queueObjectName), eq("resume"),
                                        any(Object[].class), any(String[].class));
                });
        }

        @Test
        void testResumeQueueWithException() throws Exception {
                String queueName = "errorQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));
                when(mbeanServer.invoke(eq(queueObjectName), eq("resume"),
                                any(Object[].class), any(String[].class)))
                                .thenThrow(new RuntimeException("Resume error"));

                withLoggerOff(() -> {
                        boolean result = service.resumeQueue(queueName);

                        assertThat(result).isFalse();
                });
        }

        @Test
        void testClearQueueSuccess() throws Exception {
                String queueName = "testQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));
                when(mbeanServer.invoke(eq(queueObjectName), eq("removeAllMessages"),
                                any(Object[].class), any(String[].class))).thenReturn(5L);

                withLoggerOff(() -> {
                        boolean result = service.clearQueue(queueName);

                        assertThat(result).isTrue();
                        verify(mbeanServer).invoke(eq(queueObjectName), eq("removeAllMessages"),
                                        any(Object[].class), any(String[].class));
                });
        }

        private void withLoggerOff(ThrowingRunnable action) throws Exception {
                Logger logger = (Logger) LoggerFactory.getLogger(TurQueueManagementService.class);
                Level previousLevel = logger.getLevel();
                logger.setLevel(Level.OFF);
                try {
                        action.run();
                } finally {
                        logger.setLevel(previousLevel);
                }
        }

        @FunctionalInterface
        private interface ThrowingRunnable {
                void run() throws Exception;
        }

        @Test
        void testClearQueueWithNonExistentQueue() {
                String queueName = "nonExistentQueue";

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.emptySet());

                boolean result = service.clearQueue(queueName);

                assertThat(result).isFalse();
        }

        @Test
        void testGetQueueMessagesWithLongDeliveryCount() throws Exception {
                String queueName = "testQueue";
                ObjectName queueObjectName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue="
                                                + queueName);
                ObjectInstance instance = new ObjectInstance(queueObjectName, "QueueImpl");

                when(mbeanServer.queryMBeans(any(ObjectName.class), isNull()))
                                .thenReturn(Collections.singleton(instance));

                Map<String, Object> message = new HashMap<>();
                message.put("messageID", "msg-1");
                message.put("text", "Test");
                message.put("timestamp", System.currentTimeMillis());
                message.put("deliveryCount", 5L);
                message.put("type", "TEXT");
                message.put("size", 50);

                Object[] messages = new Object[] { message };

                when(mbeanServer.invoke(eq(queueObjectName), eq("browse"),
                                any(Object[].class), any(String[].class))).thenReturn(messages);

                List<TurQueueMessage> result = service.getQueueMessages(queueName, 10);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getDeliveryCount()).isEqualTo(5);
                assertThat(result.get(0).getSize()).isEqualTo(50L);
        }

        @Test
        void testGetAllQueuesWithNullAttributes() throws Exception {
                ObjectName pattern = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue=*");

                ObjectName queueName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue=nullQueue,address=nullAddr");

                ObjectInstance instance = new ObjectInstance(queueName, "QueueImpl");
                Set<ObjectInstance> instances = Collections.singleton(instance);

                when(mbeanServer.queryMBeans(eq(pattern), isNull())).thenReturn(instances);
                when(mbeanServer.getAttribute(queueName, "MessageCount")).thenReturn(null);
                when(mbeanServer.getAttribute(queueName, "ConsumerCount")).thenReturn(null);
                when(mbeanServer.getAttribute(queueName, "Paused")).thenReturn(null);
                when(mbeanServer.getAttribute(queueName, "Temporary")).thenReturn(null);

                List<TurQueueInfo> queues = service.getAllQueues();

                assertThat(queues).hasSize(1);
                TurQueueInfo queue = queues.get(0);
                assertThat(queue.getMessageCount()).isZero();
                assertThat(queue.getConsumerCount()).isZero();
                assertThat(queue.isPaused()).isFalse();
                assertThat(queue.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void testGetAllQueuesWithPausedQueue() throws Exception {
                ObjectName pattern = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue=*");

                ObjectName queueName = new ObjectName(
                                "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=anycast,queue=pausedQueue,address=addr");

                ObjectInstance instance = new ObjectInstance(queueName, "QueueImpl");

                when(mbeanServer.queryMBeans(eq(pattern), isNull())).thenReturn(Collections.singleton(instance));
                when(mbeanServer.getAttribute(queueName, "MessageCount")).thenReturn(0L);
                when(mbeanServer.getAttribute(queueName, "ConsumerCount")).thenReturn(0L);
                when(mbeanServer.getAttribute(queueName, "Paused")).thenReturn(true);
                when(mbeanServer.getAttribute(queueName, "Temporary")).thenReturn(true);

                List<TurQueueInfo> queues = service.getAllQueues();

                assertThat(queues).hasSize(1);
                TurQueueInfo queue = queues.get(0);
                assertThat(queue.isPaused()).isTrue();
                assertThat(queue.isTemporary()).isTrue();
                assertThat(queue.getStatus()).isEqualTo("PAUSED");
        }
}
