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

import java.net.URI;
import java.time.Duration;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Base class for Redis-backed Logback appenders. Manages a {@link RedisClient}
 * life cycle (start / stop) backed by a pooled connection provider and
 * exposes configuration knobs for subclasses.
 *
 * <p>Entries are stored in a Redis LIST keyed by {@link #key}. Subclasses are
 * responsible for the actual push ({@code LPUSH}) and, optionally, for
 * trimming the list via {@link #maxEntries} to keep it bounded.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Setter
public class TurRedisAppenderBase extends AppenderBase<ILoggingEvent> {
    protected boolean enabled;
    /** Redis URI, e.g. {@code redis://host:6379/0} or {@code rediss://...} for TLS. */
    protected String connectionString;
    /** Redis LIST key that receives the log entries. */
    protected String key;
    /**
     * When greater than zero, the LIST is trimmed to this size after each push
     * using {@code LTRIM key 0 (maxEntries - 1)}, keeping only the newest
     * entries. Zero disables trimming (unbounded growth).
     */
    protected int maxEntries;
    protected RedisClient pool;

    @Override
    public void start() {
        try {
            URI uri = URI.create(connectionString);
            ConnectionPoolConfig cfg = new ConnectionPoolConfig();
            cfg.setMaxTotal(50);
            cfg.setMaxWait(Duration.ofSeconds(2));
            this.pool = RedisClient.builder()
                    .hostAndPort(JedisURIHelper.getHostAndPort(uri))
                    .clientConfig(DefaultJedisClientConfig.builder(uri).build())
                    .poolConfig(cfg)
                    .build();
            super.start();
        } catch (Exception e) {
            addError("Failed to initialize Redis Appender", e);
        }
    }

    @Override
    public void stop() {
        if (pool != null) {
            pool.close();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // Implementation in subclass
    }
}
