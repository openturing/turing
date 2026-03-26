package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for TurToolDescriptionService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurToolDescriptionServiceTest {

    private TurToolDescriptionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TurToolDescriptionService();
        // Trigger @PostConstruct manually
        service.load();
    }

    @Test
    void loadShouldNotThrow() {
        // If we got here, load() succeeded
        assertThat(service).isNotNull();
    }

    @Test
    void getDescriptionShouldReturnNullForUnknownTool() {
        assertThat(service.getDescription("nonexistent_tool_xyz")).isNull();
    }

    @Test
    void hasDescriptionShouldReturnFalseForUnknownTool() {
        assertThat(service.hasDescription("nonexistent_tool_xyz")).isFalse();
    }

    @Test
    void getDescriptionShouldReturnNullForNull() {
        assertThat(service.getDescription(null)).isNull();
    }

    @Test
    void hasDescriptionShouldReturnFalseForNull() {
        assertThat(service.hasDescription(null)).isFalse();
    }

    @Test
    void loadedDescriptionsShouldBeConsistentBetweenGetAndHas() {
        // If any description was loaded, get and has should agree
        String knownTool = "search_site";
        if (service.hasDescription(knownTool)) {
            assertThat(service.getDescription(knownTool)).isNotNull();
            assertThat(service.getDescription(knownTool)).isNotBlank();
        }
    }
}
