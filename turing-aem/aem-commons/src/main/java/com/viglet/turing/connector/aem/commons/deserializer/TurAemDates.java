package com.viglet.turing.connector.aem.commons.deserializer;

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
public class TurAemDates extends StdDeserializer<Date> {

    private static final SimpleDateFormat[] DATE_FORMATTERS = new SimpleDateFormat[]{
            new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'"),
            new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'"),
            new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSX"),
            new SimpleDateFormat("dd/MM/yyyy' 'HH'h'mm")
    };

    public TurAemDates() {
        this(null);
    }

    public TurAemDates(Class<?> vc) {
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
        if (log.isDebugEnabled()) {
            log.debug("Date was not found: {}", node.textValue());
        }
        return null;
    }
}
