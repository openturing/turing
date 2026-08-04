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
package com.viglet.turing.logging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T872 / §LXVIII.3 — the nightly housekeeping job that purges the MongoDB
 * logging collections (server, AEM, indexing) of entries older than
 * {@code turing.logging.purge.days}.
 *
 * <p>
 * It used to live on {@code TurSNSiteSearchCachedAPI}, which meant the
 * {@code @Cacheable} search seam — a load-bearing primitive T858 had just spent a
 * task making hard to misuse — was reached through a bean whose constructor took
 * seven MongoDB parameters it never read. Two responsibilities sharing one bean
 * is two classes wearing one name; they are separated here so each constructor
 * describes only what its own methods use.
 * </p>
 *
 * <p>
 * Behaviour is unchanged: same cron, same {@code @SchedulerLock} name (so a
 * cluster still runs it once), same early return when
 * {@code turing.mongodb.enabled} is false.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLoggingPurgeService {

    public static final String DATE = "date";

    private final boolean enabled;
    private final String connectionString;
    private final String databaseName;
    private final String serverCollectionName;
    private final String indexingCollectionName;
    private final String aemCollectionName;
    private final int purgeDays;

    public TurLoggingPurgeService(
            @Value("${turing.mongodb.enabled:false}") boolean enabled,
            @Value("${turing.mongodb.uri:'mongodb://localhost:27017'}") String connectionString,
            @Value("${turing.logging.database:'turingLog'}") String databaseName,
            @Value("${turing.logging.collection.server:'server'}") String serverCollectionName,
            @Value("${turing.logging.collection.indexing:'indexing'}") String indexingCollectionName,
            @Value("${turing.logging.collection.aem:'aem'}") String aemCollectionName,
            @Value("${turing.logging.purge.days:30}") int purgeDays) {
        this.enabled = enabled;
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.serverCollectionName = serverCollectionName;
        this.indexingCollectionName = indexingCollectionName;
        this.aemCollectionName = aemCollectionName;
        this.purgeDays = purgeDays;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "purgeMongoDBLogs", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void purgeMongoDBLogs() {
        if (!enabled) {
            return;
        }
        Date purgeDate = getPurgeDate();
        log.info("Executing housekeeping log task, purging logs older than {}", purgeDate);
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            Bson filter = Filters.lt(DATE, purgeDate);
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            long serverDeleted = database.getCollection(serverCollectionName)
                    .deleteMany(filter).getDeletedCount();
            long aemDeleted = database.getCollection(aemCollectionName)
                    .deleteMany(filter).getDeletedCount();
            long indexingDeleted = database.getCollection(indexingCollectionName)
                    .deleteMany(filter).getDeletedCount();
            log.info("Purged logs: server={}, aem={}, indexing={}", serverDeleted, aemDeleted,
                    indexingDeleted);
        }
    }

    /**
     * The cut-off the purge filter compares against. The MongoDB driver binds a
     * {@link Date}, so the boundary is computed with {@code java.time} and converted
     * once at that edge rather than with a mutable {@code Calendar}.
     */
    Date getPurgeDate() {
        return Date.from(Instant.now().minus(purgeDays, ChronoUnit.DAYS));
    }
}
