/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.sn.search;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurSNSiteSearchCachedAPI {
    public static final String DATE = "date";
    private final TurSNSearchProcess turSNSearchProcess;
    private final boolean enabled;
    private final String connectionString;
    private final String databaseName;
    private final String serverCollectionName;
    private final String indexingCollectionName;
    private final String aemCollectionName;
    private final int purgeDays;

    public TurSNSiteSearchCachedAPI(TurSNSearchProcess turSNSearchProcess,
            @Value("${turing.mongodb.enabled:false}") boolean enabled,
            @Value("${turing.mongodb.uri:'mongodb://localhost:27017'}") String connectionString,
            @Value("${turing.logging.database:'turingLog'}") String databaseName,
            @Value("${turing.logging.collection.server:'server'}") String serverCollectionName,
            @Value("${turing.logging.collection.indexing:'indexing'}") String indexingCollectionName,
            @Value("${turing.logging.collection.aem:'aem'}") String aemCollectionName,
            @Value("${turing.logging.purge.days:30}") int purgeDays) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.enabled = enabled;
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.serverCollectionName = serverCollectionName;
        this.indexingCollectionName = indexingCollectionName;
        this.aemCollectionName = aemCollectionName;
        this.purgeDays = purgeDays;
    }

    @CacheEvict(value = "searchAPI", allEntries = true)
    @Scheduled(fixedRateString = "${turing.search.cache.ttl.seconds:86400000}")
    public void cleanSearchCache() {
        log.info("Cleaning Search API");
    }

    @Cacheable(value = "searchAPI", key = "#cacheKey")
    public TurSNSiteSearchBean searchCached(String cacheKey,
            TurSNSiteSearchContext turSNSiteSearchContext) {
        log.info("Search cache key: {}", cacheKey);
        return turSNSearchProcess.search(turSNSiteSearchContext);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "purgeMongoDBLogs", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void purgeMongoDBLogs() {
        if (!enabled)
            return;
        Date purgeDate = getPurgeDate();
        log.info("Executing housekeeping log task, purging logs older than {}", purgeDate);
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            Bson filter = Filters.lt(DATE, purgeDate);
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            long serverDeleted = database.getCollection(serverCollectionName).deleteMany(filter).getDeletedCount();
            long aemDeleted = database.getCollection(aemCollectionName).deleteMany(filter).getDeletedCount();
            long indexingDeleted = database.getCollection(indexingCollectionName).deleteMany(filter).getDeletedCount();
            log.info("Purged logs: server={}, aem={}, indexing={}", serverDeleted, aemDeleted, indexingDeleted);
        }
    }

    private @NotNull Date getPurgeDate() {
        Date today = new Date();
        Calendar cal = new GregorianCalendar();
        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, purgeDays * (-1));
        return cal.getTime();
    }
}
