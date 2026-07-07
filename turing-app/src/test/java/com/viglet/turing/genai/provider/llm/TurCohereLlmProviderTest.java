package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@ExtendWith(MockitoExtension.class)
class TurCohereLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurCohereLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
    }

    @Test
    void testGetPluginType() {
        assertEquals("cohere", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_missingApiKey() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, null));
    }

    @Test
    void testCreateChatModel_defaultsToCompatibilityEndpoint() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createChatModel(instance, "cohere-key"));
    }

    @Test
    void testCreateEmbeddingModel_supported() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createEmbeddingModel(instance, "cohere-key"));
    }

    @Test
    void testCreateEmbeddingModel_missingApiKey() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createEmbeddingModel(instance, null));
    }
}
