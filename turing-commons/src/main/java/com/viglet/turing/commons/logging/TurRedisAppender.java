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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jetbrains.annotations.NotNull;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

/**
 * Redis counterpart of {@link TurMongoDBAppender}. Captures each logging event
 * as a {@link TurLoggingGeneral} record and pushes the JSON payload to a Redis
 * LIST via {@code LPUSH}. Optionally trims the list to {@code maxEntries} so
 * the key doesn't grow unbounded.
 *
 * <p>The I/O is offloaded to an internal executor to keep the logging thread
 * non-blocking, matching the behavior of the MongoDB appender.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Setter
public class TurRedisAppender extends TurRedisAppenderBase {

    public static final int MAX_LENGTH_PACKAGE_NAME = 40;
    private static final Abbreviator ABBREVIATOR = new TargetLengthBasedClassNameAbbreviator(1);
    private static String cachedHostName;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JodaModule())
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .build();

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    static {
        try {
            cachedHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            cachedHostName = "unknown";
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled || pool == null) {
            return;
        }

        TurLoggingGeneral logEntry = TurLoggingGeneral.builder()
                .level(event.getLevel().toString())
                .logger(abbreviatePackage(event.getLoggerName()))
                .message(event.getFormattedMessage())
                .date(new Date(event.getTimeStamp()))
                .stackTrace(getStackTrace(event))
                .clusterNode(cachedHostName)
                .build();

        executor.submit(() -> {
            try {
                String json = MAPPER.writeValueAsString(logEntry);
                pool.lpush(key, json);
                if (maxEntries > 0) {
                    pool.ltrim(key, 0, maxEntries - 1L);
                }
            } catch (Exception e) {
                log.info("Failed to log to Redis: {}", e.getMessage());
            }
        });
    }

    @Override
    public void stop() {
        executor.shutdown();
        super.stop();
    }

    private static @NotNull String getStackTrace(ILoggingEvent event) {
        StringBuilder stackTraceBuilder = new StringBuilder();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            String throwableStr = ThrowableProxyUtil.asString(throwableProxy);
            stackTraceBuilder.append(throwableStr);
            stackTraceBuilder.append(CoreConstants.LINE_SEPARATOR);
        }
        return stackTraceBuilder.toString();
    }

    private static @NotNull String abbreviatePackage(String packageName) {
        if (packageName == null)
            return "";
        if (packageName.length() <= MAX_LENGTH_PACKAGE_NAME)
            return packageName;
        return ABBREVIATOR.abbreviate(packageName);
    }
}
