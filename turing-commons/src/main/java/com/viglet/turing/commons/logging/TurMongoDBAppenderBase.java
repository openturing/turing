package com.viglet.turing.commons.logging;

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

@Slf4j
@Setter
public class TurMongoDBAppenderBase extends AppenderBase<ILoggingEvent> {
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