package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for {@link TurToolSchemaSanitizerCallback}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurToolSchemaSanitizerCallbackTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Mock
    private ToolCallback delegate;

    private void stubSchema(String schema) {
        when(delegate.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("config_tool")
                .description("desc")
                .inputSchema(schema)
                .build());
    }

    @Test
    void removesNullDefaultThatCrashesGeminiBinder() {
        // Reproduces the failing reference chain: properties -> config_file -> default: null
        stubSchema("""
                {"type":"object","properties":{"config_file":{"type":"string","default":null}}}
                """);

        ToolCallback wrapped = TurToolSchemaSanitizerCallback.wrap(new ToolCallback[] { delegate })[0];

        JsonNode configFile = MAPPER.readTree(wrapped.getToolDefinition().inputSchema())
                .path("properties").path("config_file");
        assertThat(configFile.has("default")).isFalse();
        assertThat(configFile.path("type").asString()).isEqualTo("string");
    }

    @Test
    void preservesRealNonNullDefaults() {
        stubSchema("""
                {"type":"object","properties":{"limit":{"type":"integer","default":10}}}
                """);

        ToolCallback wrapped = TurToolSchemaSanitizerCallback.wrap(new ToolCallback[] { delegate })[0];

        // No null present → callback returned untouched (same instance).
        assertThat(wrapped).isSameAs(delegate);
    }

    @Test
    void leavesSchemaWithoutNullsUntouched() {
        stubSchema("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}");

        ToolCallback wrapped = TurToolSchemaSanitizerCallback.wrap(new ToolCallback[] { delegate })[0];

        assertThat(wrapped).isSameAs(delegate);
    }

    @Test
    void keepsCallbackWhenSchemaIsBlank() {
        // DefaultToolDefinition forbids a blank schema, so mock the definition
        // directly to exercise the defensive blank-schema guard.
        ToolDefinition blankDef = mock(ToolDefinition.class);
        when(blankDef.inputSchema()).thenReturn("");
        when(delegate.getToolDefinition()).thenReturn(blankDef);

        ToolCallback wrapped = TurToolSchemaSanitizerCallback.wrap(new ToolCallback[] { delegate })[0];

        assertThat(wrapped).isSameAs(delegate);
    }
}
