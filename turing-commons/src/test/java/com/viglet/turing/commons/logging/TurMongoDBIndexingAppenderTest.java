package com.viglet.turing.commons.logging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.spi.ILoggingEvent;

class TurMongoDBIndexingAppenderTest {

    @Test
    void shouldSkipWhenDisabled() {
        TurMongoDBIndexingAppender appender = new TurMongoDBIndexingAppender();
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getArgumentArray()).thenReturn(new Object[] { Map.of("k", "v") });

        appender.setEnabled(false);
        appender.setCollection(collection);

        assertThatCode(() -> appender.append(event)).doesNotThrowAnyException();
        verify(collection, never()).insertOne(any(Document.class));
    }

    @Test
    void shouldInsertEachArgumentAsDocumentWhenEnabled() {
        TurMongoDBIndexingAppender appender = new TurMongoDBIndexingAppender();
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getArgumentArray()).thenReturn(new Object[] { Map.of("a", 1), Map.of("b", 2) });

        appender.setEnabled(true);
        appender.setCollection(collection);

        assertThatCode(() -> appender.append(event)).doesNotThrowAnyException();
        verify(collection, times(2)).insertOne(any(Document.class));
    }
}
