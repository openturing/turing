package com.viglet.turing.sprinklr.client.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TurSprinklrDates extends StdDeserializer<Date> {

    private static final SimpleDateFormat[] DATE_FORMATTERS = new SimpleDateFormat[]{
            new SimpleDateFormat("MMM' 'dd', 'yyyy', 'HH:mm:ss' 'a", Locale.ENGLISH)
    };

    public TurSprinklrDates() {
        this(null);
    }

    public TurSprinklrDates(Class<?> vc) {
        super(vc);
    }

    @Override
    public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        if (StringUtils.isNumeric(node.textValue())) {
            String timestamp = node.textValue().trim();
            try {
                return new Date(Long.parseLong(timestamp));
            } catch (NumberFormatException e) {
                log.error("Unable to deserialize timestamp: {}", timestamp, e);
            }
        }
        for (SimpleDateFormat formatter : DATE_FORMATTERS) {
            try {
                return formatter.parse(node.textValue());
            } catch (ParseException ignored) {
                // ignored
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Date was not found: {}", node.textValue());
        }
        return null;
    }
}
