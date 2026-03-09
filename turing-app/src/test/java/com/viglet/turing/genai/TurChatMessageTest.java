package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurChatMessageTest {

    @Test
    void shouldCreateMessageUsingBuilder() {
        TurChatMessage message = TurChatMessage.builder()
                .enabled(true)
                .text("hello")
                .build();

        assertTrue(message.isEnabled());
        assertEquals("hello", message.getText());
    }

    @Test
    void shouldAllowUpdatingFieldsUsingSetters() {
        TurChatMessage message = new TurChatMessage(true, "old");

        message.setEnabled(false);
        message.setText("new");

        assertFalse(message.isEnabled());
        assertEquals("new", message.getText());
    }
}
