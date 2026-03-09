package com.viglet.turing.persistence.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hibernate.generator.EventType;
import org.junit.jupiter.api.Test;

class TurUuidGeneratorTest {

    private final TurUuidGenerator generator = new TurUuidGenerator();

    @Test
    void shouldKeepAssignedIdentifierWhenCurrentValueIsNotNull() {
        Object currentValue = "existing-id";

        Object generated = generator.generate(null, new Object(), currentValue, EventType.INSERT);

        assertSame(currentValue, generated);
    }

    @Test
    void shouldGenerateUuidWhenCurrentValueIsNull() {
        Object generated = generator.generate(null, new Object(), null, EventType.INSERT);

        assertNotNull(generated);
        assertTrue(generated instanceof String);
        assertEquals(36, ((String) generated).length());
    }

    @Test
    void shouldExposeGeneratorCapabilities() {
        assertFalse(generator.generatedOnExecution());
        assertTrue(generator.allowAssignedIdentifiers());
        assertEquals(java.util.EnumSet.of(EventType.INSERT), generator.getEventTypes());
    }
}
