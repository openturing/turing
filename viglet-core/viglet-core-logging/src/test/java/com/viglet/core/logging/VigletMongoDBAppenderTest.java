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

class VigletMongoDBAppenderTest {

    @Test
    void shouldSkipWhenDisabledOrCollectionIsNull() {
        VigletMongoDBAppender appender = new VigletMongoDBAppender();
        ILoggingEvent event = mock(ILoggingEvent.class);

        appender.setEnabled(false);
        assertThatCode(() -> appender.append(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldInsertDocumentAsynchronouslyWhenEnabled() {
        VigletMongoDBAppender appender = new VigletMongoDBAppender();
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);

        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.INFO);
        when(event.getLoggerName())
                .thenReturn("com.viglet.core.logging.SomeVeryLongLoggerNameThatShouldBeAbbreviated");
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
