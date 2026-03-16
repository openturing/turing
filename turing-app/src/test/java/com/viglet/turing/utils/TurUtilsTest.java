package com.viglet.turing.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TurUtilsTest {

    @Test
    void getUrlTemplateShouldConcatenateWithSlash() {
        assertEquals("http://localhost/123", TurUtils.getUrlTemplate("http://localhost", "123"));
    }

    @Test
    void getUrlTemplateShouldHandleEmptyId() {
        assertEquals("http://localhost/", TurUtils.getUrlTemplate("http://localhost", ""));
    }

    @Test
    void getUrlTemplateShouldHandleTrailingSlashInServiceUrl() {
        assertEquals("http://localhost//abc", TurUtils.getUrlTemplate("http://localhost/", "abc"));
    }
}
