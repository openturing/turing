/*
 * Copyright (C) 2016-2024 the original author or authors.
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

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for managing Artemis queues.
 *
 * @author Alexandre Oliveira
 */
@Slf4j
@RestController
@RequestMapping("/api/artemis")
@Tag(name = "Artemis Queue Management", description = "Artemis Queue Management API")
public class TurQueueManagementAPI {

        public static final String SUCCESS = "success";
        public static final String MESSAGE = "message";
        public static final String QUEUE_NAME = "queueName";
        private final TurQueueManagementService queueManagementService;

        public TurQueueManagementAPI(TurQueueManagementService queueManagementService) {
                this.queueManagementService = queueManagementService;
        }

        @Operation(summary = "List all queues", description = "Get information about all existing Artemis queues")
        @GetMapping
        public ResponseEntity<List<TurQueueInfo>> listQueues() {
                try {
                        List<TurQueueInfo> queues = queueManagementService.getAllQueues();
                        return ResponseEntity.ok(queues);
                } catch (Exception e) {
                        log.error("Error listing queues", e);
                        return ResponseEntity.internalServerError().build();
                }
        }

        @Operation(summary = "Get queue messages", description = "Get messages from a specific queue")
        @GetMapping("/{queueName}/messages")
        public ResponseEntity<List<TurQueueMessage>> getQueueMessages(
                        @Parameter(description = "Queue name") @PathVariable String queueName,
                        @Parameter(description = "Maximum number of messages to retrieve") @RequestParam(defaultValue = "50") int maxMessages) {
                try {
                        List<TurQueueMessage> messages = queueManagementService.getQueueMessages(queueName,
                                        maxMessages);
                        return ResponseEntity.ok(messages);
                } catch (Exception e) {
                        log.error("Error getting messages from queue {}", queueName, e);
                        return ResponseEntity.internalServerError().build();
                }
        }

        @Operation(summary = "Pause queue", description = "Pause message consumption for a specific queue")
        @PostMapping("/{queueName}/pause")
        public ResponseEntity<Map<String, Object>> pauseQueue(
                        @Parameter(description = "Queue name") @PathVariable String queueName) {

                boolean success = queueManagementService.pauseQueue(queueName);
                if (success) {
                        return ResponseEntity.ok(Map.of(
                                        SUCCESS, true,
                                        MESSAGE, "Queue paused successfully",
                                        QUEUE_NAME, queueName));
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                                SUCCESS, false,
                                                MESSAGE, "Failed to pause queue: current state prevents pausing",
                                                QUEUE_NAME, queueName));
        }

        @Operation(summary = "Resume queue", description = "Resume message consumption for a specific queue")
        @PostMapping("/{queueName}/resume")
        public ResponseEntity<Map<String, Object>> resumeQueue(
                        @Parameter(description = "Queue name") @PathVariable String queueName) {

                boolean success = queueManagementService.resumeQueue(queueName);
                if (success) {
                        return ResponseEntity.ok(Map.of(
                                        SUCCESS, true,
                                        MESSAGE, "Queue resumed successfully",
                                        QUEUE_NAME, queueName));
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                                SUCCESS, false,
                                                MESSAGE, "Failed to resume queue",
                                                QUEUE_NAME, queueName));
        }

        @Operation(summary = "Start queue", description = "Start/resume message consumption for a specific queue (alias for resume)")
        @PostMapping("/{queueName}/start")
        public ResponseEntity<Map<String, Object>> startQueue(
                        @Parameter(description = "Queue name") @PathVariable String queueName) {
                return resumeQueue(queueName);
        }

        @Operation(summary = "Stop queue", description = "Stop/pause message consumption for a specific queue (alias for pause)")
        @PostMapping("/{queueName}/stop")
        public ResponseEntity<Map<String, Object>> stopQueue(
                        @Parameter(description = "Queue name") @PathVariable String queueName) {
                return pauseQueue(queueName);
        }

        @Operation(summary = "Clear queue", description = "Remove all messages from a specific queue")
        @DeleteMapping("/{queueName}/messages")
        public ResponseEntity<Map<String, Object>> clearQueue(
                        @Parameter(description = "Queue name") @PathVariable String queueName) {

                boolean success = queueManagementService.clearQueue(queueName);
                if (success) {
                        return ResponseEntity.ok(Map.of(
                                        SUCCESS, true,
                                        MESSAGE, "Queue cleared successfully",
                                        QUEUE_NAME, queueName));
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                                SUCCESS, false,
                                                MESSAGE, "Failed to clear queue",
                                                QUEUE_NAME, queueName));

        }

        @Operation(summary = "Get queue info", description = "Get detailed information about a specific queue")
        @GetMapping("/{queueName}")
        public ResponseEntity<TurQueueInfo> getQueueInfo(
                        @Parameter(description = "Queue name") @PathVariable String queueName) {
                try {
                        List<TurQueueInfo> queues = queueManagementService.getAllQueues();
                        TurQueueInfo queueInfo = queues.stream()
                                        .filter(queue -> queueName.equals(queue.getName()))
                                        .findFirst()
                                        .orElse(null);

                        if (queueInfo != null) {
                                return ResponseEntity.ok(queueInfo);
                        } else {
                                return ResponseEntity.notFound().build();
                        }
                } catch (Exception e) {
                        log.error("Error getting queue info for {}", queueName, e);
                        return ResponseEntity.internalServerError().build();
                }
        }
}