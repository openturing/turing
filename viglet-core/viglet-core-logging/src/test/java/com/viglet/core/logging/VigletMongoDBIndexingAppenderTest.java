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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoCollection;

import ch.qos.logback.classic.spi.ILoggingEvent;

class VigletMongoDBIndexingAppenderTest {

    @Test
    void shouldSkipWhenDisabled() {
        VigletMongoDBIndexingAppender appender = new VigletMongoDBIndexingAppender();
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
        VigletMongoDBIndexingAppender appender = new VigletMongoDBIndexingAppender();
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
