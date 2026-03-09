package com.viglet.turing.commons.logging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class TurMongoDBAppenderTest {

    @Test
    void shouldSkipWhenDisabledOrCollectionIsNull() {
        TurMongoDBAppender appender = new TurMongoDBAppender();
        ILoggingEvent event = mock(ILoggingEvent.class);

        appender.setEnabled(false);
        assertThatCode(() -> appender.append(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldInsertDocumentAsynchronouslyWhenEnabled() {
        TurMongoDBAppender appender = new TurMongoDBAppender();
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);

        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.INFO);
        when(event.getLoggerName())
                .thenReturn("com.viglet.turing.commons.logging.SomeVeryLongLoggerNameThatShouldBeAbbreviated");
        when(event.getFormattedMessage()).thenReturn("test log");
        when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());
        when(event.getThrowableProxy()).thenReturn(null);

        appender.setEnabled(true);
        appender.setCollection(collection);

        assertThatCode(() -> appender.append(event)).doesNotThrowAnyException();
        verify(collection, timeout(2000)).insertOne(any(Document.class));

        appender.stop();
    }
}
