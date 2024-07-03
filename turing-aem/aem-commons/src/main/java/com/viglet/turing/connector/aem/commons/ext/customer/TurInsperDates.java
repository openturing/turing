package com.viglet.turing.connector.aem.commons.ext.customer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class TurInsperDates extends StdDeserializer<Date> {

    private static final SimpleDateFormat[] DATE_FORMATTERS = new SimpleDateFormat[]{
            new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'"),
            new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'"),
            new SimpleDateFormat("dd/MM/yyyy' 'HH'h'mm")
    };

    public TurInsperDates() {
        this(null);
    }

    public TurInsperDates(Class<?> vc) {
        super(vc);
    }

    @Override
    public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        for (SimpleDateFormat formatter : DATE_FORMATTERS) {
            try {
                return formatter.parse(node.textValue());
            } catch (ParseException ignored) {
            }
        }
        log.warn("Date was not found: {}", node.textValue());
        return null;
    }
}
