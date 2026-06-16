/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.logging;

import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for MongoDB-backed Logback appenders. Manages a pooled
 * {@link MongoClient} life cycle (start / stop) and exposes the target
 * collection plus configuration knobs for subclasses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Setter
public class VigletMongoDBAppenderBase extends AppenderBase<ILoggingEvent> {
    protected boolean enabled;
    protected String connectionString;
    protected String databaseName;
    protected String collectionName;
    protected MongoCollection<Document> collection;
    private MongoClient mongoClient; // Keep a reference to close it later

    @Override
    public void start() {
        try {
            ConnectionString connString = new ConnectionString(connectionString);

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connString)
                    .applyToConnectionPoolSettings(builder -> builder.maxSize(50) // Adjust based on your app load
                            .maxWaitTime(2, TimeUnit.SECONDS) // Don't hang forever
                    )
                    .build();

            this.mongoClient = MongoClients.create(settings);
            this.collection = mongoClient
                    .getDatabase(databaseName)
                    .getCollection(collectionName);

            super.start();
        } catch (Exception e) {
            addError("Failed to initialize MongoDB Appender", e);
        }
    }

    @Override
    public void stop() {
        if (mongoClient != null) {
            mongoClient.close();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        // Implementation in subclass
    }
}
