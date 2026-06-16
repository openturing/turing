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

import java.util.Arrays;

import org.bson.Document;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes each argument of the logging event as JSON and inserts it as a
 * document into the configured MongoDB collection — used for the structured
 * indexing log, whose events carry a status object as a message argument.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Setter
public class VigletMongoDBIndexingAppender extends VigletMongoDBAppenderBase {

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || collection == null) {
            return;
        }
        Arrays.stream(eventObject.getArgumentArray())
                .forEach(object -> collection.insertOne(Document.parse(new ObjectMapper().writeValueAsString(object))));
    }
}
