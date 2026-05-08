/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.service.chatanalytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the {@link TurChatAnalyticsStore} bean based on
 * {@code turing.logging.engine}. Mirrors {@code TurChatMemoryConfig} so a
 * single env var routes all chat telemetry — both per-message memory and
 * aggregated session analytics — to the same backend.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Configuration
public class TurChatAnalyticsConfig {

    private final String loggingEngine;
    private final boolean mongoEnabled;
    private final String mongoUri;
    private final boolean redisEnabled;
    private final String redisUri;
    private final String databaseName;
    private final String collectionName;

    private TurChatAnalyticsStore createdStore;

    public TurChatAnalyticsConfig(
            @Value("${turing.logging.engine:none}") String loggingEngine,
            @Value("${turing.mongodb.enabled:false}") boolean mongoEnabled,
            @Value("${turing.mongodb.uri:mongodb://localhost:27017}") String mongoUri,
            @Value("${turing.redis.enabled:false}") boolean redisEnabled,
            @Value("${turing.redis.uri:redis://localhost:6379/0}") String redisUri,
            @Value("${turing.logging.database:turingLog}") String databaseName,
            @Value("${turing.logging.collection.chat-analytics:chat_analytics}") String collectionName) {
        this.loggingEngine = loggingEngine;
        this.mongoEnabled = mongoEnabled;
        this.mongoUri = mongoUri;
        this.redisEnabled = redisEnabled;
        this.redisUri = redisUri;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @Bean
    public TurChatAnalyticsStore turChatAnalyticsStore() {
        TurChatAnalyticsEngine engine = TurChatAnalyticsEngine.fromConfig(loggingEngine);
        switch (engine) {
            case MONGODB:
                if (!mongoEnabled) {
                    log.info("Chat analytics: turing.logging.engine=mongodb but turing.mongodb.enabled=false — disabled");
                    createdStore = new TurNoOpChatAnalyticsStore();
                } else {
                    log.info("Chat analytics: using MongoDB store (database={}, collection={})",
                            databaseName, collectionName);
                    createdStore = new TurMongoChatAnalyticsStore(mongoUri, databaseName, collectionName);
                }
                break;
            case REDIS:
                if (!redisEnabled) {
                    log.info("Chat analytics: turing.logging.engine=redis but turing.redis.enabled=false — disabled");
                    createdStore = new TurNoOpChatAnalyticsStore();
                } else {
                    log.info("Chat analytics: using Redis store (key prefix={})", collectionName);
                    createdStore = new TurRedisChatAnalyticsStore(redisUri, collectionName);
                }
                break;
            default:
                log.info("Chat analytics: turing.logging.engine=none — disabled");
                createdStore = new TurNoOpChatAnalyticsStore();
        }
        return createdStore;
    }

    @PreDestroy
    public void close() {
        if (createdStore instanceof TurMongoChatAnalyticsStore m) {
            m.close();
        } else if (createdStore instanceof TurRedisChatAnalyticsStore r) {
            r.close();
        }
    }
}
