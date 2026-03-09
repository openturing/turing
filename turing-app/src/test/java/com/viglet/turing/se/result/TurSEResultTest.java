package com.viglet.turing.se.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TurSEResultTest {

    @Test
    void shouldBuildWithFields() {
        TurSEResult result = TurSEResult.builder()
                .fields(Map.of("title", "Hello"))
                .build();

        assertEquals("Hello", result.getFields().get("title"));
    }

    @Test
    void shouldHaveAccessibleNoArgConstructor() {
        TurSEResult result = new TurSEResult();

        assertNotNull(result);
    }
}
