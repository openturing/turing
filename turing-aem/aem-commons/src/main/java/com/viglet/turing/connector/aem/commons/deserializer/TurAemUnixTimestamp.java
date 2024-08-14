package com.viglet.turing.connector.aem.commons.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Date;

@Slf4j
public class TurAemUnixTimestamp extends StdDeserializer<Date> {

    public TurAemUnixTimestamp() {
        this(null);
    }

    public TurAemUnixTimestamp(Class<?> vc) {
        super(vc);
    }

    @Override
    public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        String timestamp = jsonParser.getText().trim();

        try {
            return new Date(Long.parseLong(timestamp) * 1000);
        } catch (NumberFormatException e) {
            log.error("Unable to deserialize timestamp: {}", timestamp, e);
            return null;
        }
    }
}
