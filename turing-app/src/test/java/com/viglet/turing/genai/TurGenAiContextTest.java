package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TurGenAiContextTest {

    @Test
    void shouldExposeExpectedCollectionConstant() {
        assertEquals("turing", TurGenAiContext.COLLECTION_NAME);
    }

    @Test
    void shouldKeepCoreFieldsAsFinalForImmutableContextState() {
        Field[] fields = TurGenAiContext.class.getDeclaredFields();

        assertTrue(Arrays.stream(fields)
                .filter(field -> field.getName().equals("vectorStore")
                        || field.getName().equals("embeddingModel")
                        || field.getName().equals("chatModel")
                        || field.getName().equals("enabled")
                        || field.getName().equals("systemPrompt"))
                .allMatch(field -> Modifier.isFinal(field.getModifiers())));
    }
}
