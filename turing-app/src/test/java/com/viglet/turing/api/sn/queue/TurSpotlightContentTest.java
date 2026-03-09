package com.viglet.turing.api.sn.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSpotlightContentTest {

    @Test
    void testGettersAndSetters() {
        TurSpotlightContent content = new TurSpotlightContent();
        content.setPosition(1);
        content.setTitle("title");
        content.setContent("content");
        content.setLink("link");
        content.setType("type");

        assertEquals(1, content.getPosition());
        assertEquals("title", content.getTitle());
        assertEquals("content", content.getContent());
        assertEquals("link", content.getLink());
        assertEquals("type", content.getType());
    }
}
