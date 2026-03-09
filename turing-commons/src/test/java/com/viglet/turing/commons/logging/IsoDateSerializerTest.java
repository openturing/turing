package com.viglet.turing.commons.logging;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

import java.util.Date;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IsoDateSerializerTest {

    @Test
    void shouldWriteNullWhenDateIsNull() throws Exception {
        IsoDateSerializer serializer = new IsoDateSerializer();
        JsonGenerator jsonGenerator = mock(JsonGenerator.class);

        serializer.serialize(null, jsonGenerator, mock(SerializationContext.class));

        verify(jsonGenerator).writeNull();
    }

    @Test
    void shouldWriteIsoDateRawValueWhenDateExists() throws Exception {
        IsoDateSerializer serializer = new IsoDateSerializer();
        JsonGenerator jsonGenerator = mock(JsonGenerator.class);

        serializer.serialize(new Date(0L), jsonGenerator, mock(SerializationContext.class));

        verify(jsonGenerator).writeRawValue(org.mockito.ArgumentMatchers.contains("ISODate("));
    }
}
