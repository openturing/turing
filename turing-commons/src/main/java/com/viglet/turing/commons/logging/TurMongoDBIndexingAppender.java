package com.viglet.turing.commons.logging;

import java.util.Arrays;

import org.bson.Document;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Setter
public class TurMongoDBIndexingAppender extends TurMongoDBAppenderBase {

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || collection == null) {
            return;
        }
        Arrays.stream(eventObject.getArgumentArray())
                .forEach(object -> collection.insertOne(Document.parse(new ObjectMapper().writeValueAsString(object))));
    }
}