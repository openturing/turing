/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.commons.logging;

import java.util.Arrays;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis counterpart of {@link TurMongoDBIndexingAppender}. Serializes each
 * argument of the logging event as JSON and pushes it to the configured Redis
 * LIST. Matches the indexing log format used by the Mongo flavor.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Setter
public class TurRedisIndexingAppender extends TurRedisAppenderBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || pool == null) {
            return;
        }
        try {
            Arrays.stream(eventObject.getArgumentArray())
                    .forEach(object -> {
                        String json = MAPPER.writeValueAsString(object);
                        pool.lpush(key, json);
                        if (maxEntries > 0) {
                            pool.ltrim(key, 0, maxEntries - 1L);
                        }
                    });
        } catch (Exception e) {
            log.info("Failed to log indexing event to Redis: {}", e.getMessage());
        }
    }
}
