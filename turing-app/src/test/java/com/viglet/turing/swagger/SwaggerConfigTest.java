package com.viglet.turing.swagger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;

class SwaggerConfigTest {

    private final SwaggerConfig swaggerConfig = new SwaggerConfig();

    @Test
    void shouldCreateOpenApiWithExpectedMetadata() {
        OpenAPI openAPI = swaggerConfig.apiInfo();

        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("Turing ES", openAPI.getInfo().getTitle());
        assertEquals("Semantic Navigation, Chatbot using Search Engine.", openAPI.getInfo().getDescription());
        assertEquals("v2.0", openAPI.getInfo().getVersion());
        assertEquals("Apache 2.0", openAPI.getInfo().getLicense().getName());
        assertEquals("http://viglet.com/turing", openAPI.getInfo().getLicense().getUrl());
        assertEquals("Turing ES Documentation", openAPI.getExternalDocs().getDescription());
        assertEquals("https://docs.viglet.com/turing", openAPI.getExternalDocs().getUrl());
    }
}
