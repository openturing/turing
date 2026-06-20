/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.viglet.turing.api.queue;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing Artemis queues via JMX.
 *
 * @author Alexandre Oliveira
 */
@Slf4j
@Service
public class TurQueueManagementService {

    private final MBeanServer mbeanServer;

    public TurQueueManagementService() {
        this.mbeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    /**
     * Get list of all queues.
     */
    public List<TurQueueInfo> getAllQueues() {
        List<TurQueueInfo> queues = new ArrayList<>();
        try {
            // Query for Artemis queue MBeans
            ObjectName objectName = new ObjectName(
                    "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue=*");
            Set<ObjectInstance> queueMBeans = mbeanServer.queryMBeans(objectName, null);
            queueMBeans.stream().map(ObjectInstance::getObjectName)
                    .forEach(queueObjectName -> getQueue(queueObjectName, queues));
        } catch (Exception e) {
            log.error("Error listing queues", e);
        }

        return queues;
    }

    private void getQueue(ObjectName queueObjectName, List<TurQueueInfo> queues) {
        try {
            String queueName = queueObjectName.getKeyProperty("queue");
            String address = queueObjectName.getKeyProperty("address");

            Long messageCount =
                    (Long) mbeanServer.getAttribute(queueObjectName, "MessageCount");
            Long consumerCount =
                    (Long) mbeanServer.getAttribute(queueObjectName, "ConsumerCount");
            Boolean paused = (Boolean) mbeanServer.getAttribute(queueObjectName, "Paused");
            Boolean temporary =
                    (Boolean) mbeanServer.getAttribute(queueObjectName, "Temporary");

            TurQueueInfo queueInfo = TurQueueInfo.builder().name(queueName).address(address)
                    .messageCount(messageCount != null ? messageCount : 0)
                    .consumerCount(consumerCount != null ? consumerCount : 0)
                    .paused(Boolean.TRUE.equals(paused))
                    .temporary(Boolean.TRUE.equals(temporary))
                    .status(paused != null && paused ? "PAUSED" : "ACTIVE").build();

            queues.add(queueInfo);
        } catch (Exception e) {
            log.warn("Error getting queue info for {}: {}", queueObjectName,
                    e.getMessage());
        }
    }

    /**
     * Get messages from a specific queue.
     */
    public List<TurQueueMessage> getQueueMessages(String queueName, int maxMessages) {
        List<TurQueueMessage> messages = new ArrayList<>();
        try {
            ObjectName queueObjectName = findQueueObjectName(queueName);
            if (queueObjectName != null) {
                // Browse messages
                Object[] result = (Object[]) mbeanServer.invoke(queueObjectName, "browse",
                        new Object[] {maxMessages}, new String[] {"int"});

                if (result != null) {
                    for (Object messageObj : result) {
                        if (messageObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> messageMap = (Map<String, Object>) messageObj;

                            TurQueueMessage message = TurQueueMessage.builder()
                                    .messageId(String.valueOf(messageMap.get("messageID")))
                                    .content(String.valueOf(messageMap.get("text")))
                                    .timestamp(convertTimestamp(messageMap.get("timestamp")))
                                    .deliveryCount(getIntValue(messageMap.get("deliveryCount")))
                                    .type(String.valueOf(messageMap.get("type")))
                                    .size(getLongValue(messageMap.get("size"))).build();

                            messages.add(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting messages from queue {}", queueName, e);
        }

        return messages;
    }

    /**
     * Pause a queue.
     */
    public boolean pauseQueue(String queueName) {
        try {
            ObjectName queueObjectName = findQueueObjectName(queueName);
            if (queueObjectName != null) {
                mbeanServer.invoke(queueObjectName, "pause", new Object[] {}, new String[] {});
                log.info("Queue {} paused successfully", queueName);
                return true;
            }
        } catch (Exception e) {
            log.error("Error pausing queue {}", queueName, e);
        }
        return false;
    }

    /**
     * Resume a queue.
     */
    public boolean resumeQueue(String queueName) {
        try {
            ObjectName queueObjectName = findQueueObjectName(queueName);
            if (queueObjectName != null) {
                mbeanServer.invoke(queueObjectName, "resume", new Object[] {}, new String[] {});
                log.info("Queue {} resumed successfully", queueName);
                return true;
            }
        } catch (Exception e) {
            log.error("Error resuming queue {}", queueName, e);
        }
        return false;
    }

    /**
     * Clear all messages from a queue.
     */
    public boolean clearQueue(String queueName) {
        try {
            ObjectName queueObjectName = findQueueObjectName(queueName);
            if (queueObjectName != null) {
                Long clearedCount = (Long) mbeanServer.invoke(queueObjectName, "removeAllMessages",
                        new Object[] {}, new String[] {});
                log.info("Cleared {} messages from queue {}", clearedCount, queueName);
                return true;
            }
        } catch (Exception e) {
            log.error("Error clearing queue {}", queueName, e);
        }
        return false;
    }

    /**
     * Find the ObjectName for a queue.
     */
    private ObjectName findQueueObjectName(String queueName) {
        try {
            ObjectName pattern = new ObjectName(
                    "org.apache.activemq.artemis:broker=\"localhost\",subcomponent=queues,routing-type=*,queue="
                            + queueName);
            Set<ObjectInstance> queueMBeans = mbeanServer.queryMBeans(pattern, null);

            if (queueMBeans.size() == 1) {
                return queueMBeans.iterator().next().getObjectName();
            } else if (queueMBeans.size() > 1) {
                log.warn("Multiple queues found for name {}, using first one", queueName);
                return queueMBeans.iterator().next().getObjectName();
            }
        } catch (Exception e) {
            log.error("Error finding queue {}", queueName, e);
        }
        return null;
    }

    private LocalDateTime convertTimestamp(Object timestamp) {
        if (timestamp instanceof Long l) return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(l),
                ZoneId.systemDefault());
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private int getIntValue(Object value) {
        if (value instanceof Integer i) {
            return i;
        } else if (value instanceof Long l) {
            return l.intValue();
        }
        return 0;
    }

    private long getLongValue(Object value) {
        if (value instanceof Long l) {
            return l;
        } else if (value instanceof Integer i) {
            return i.longValue();
        }
        return 0L;
    }
}
