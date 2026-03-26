package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import com.google.genai.Client;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * Tests for TurGeminiLlmProvider.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurGeminiLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurGeminiLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
        instance.setUrl("");
    }

    @Test
    void testGetPluginType() {
        assertEquals("gemini", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_missingApiKey_null() {
        when(optionsParser.parse(any())).thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, null));
    }

    @Test
    void testCreateChatModel_missingApiKey_empty() {
        when(optionsParser.parse(any())).thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, ""));
    }

    @Test
    void testCreateChatModel_missingApiKey_blank() {
        when(optionsParser.parse(any())).thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, "   "));
    }

    @Test
    void testCreateEmbeddingModel_unsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> provider.createEmbeddingModel(instance, "dummy-key"));
    }

    @Test
    void testCreateEmbeddingModel_unsupported_withNullKey() {
        assertThrows(UnsupportedOperationException.class,
                () -> provider.createEmbeddingModel(instance, null));
    }

    @Test
    void testCreateChatModel_withValidApiKeyDefaultModel() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        try (MockedConstruction<Client> mocked = Mockito.mockConstruction(Client.class)) {
            ChatModel model = provider.createChatModel(instance, "valid-api-key");
            assertNotNull(model);
        }
    }

    @Test
    void testCreateChatModel_withCustomModel() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn("gemini-1.5-pro");
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        try (MockedConstruction<Client> mocked = Mockito.mockConstruction(Client.class)) {
            ChatModel model = provider.createChatModel(instance, "valid-api-key");
            assertNotNull(model);
        }
    }

    @Test
    void testCreateChatModel_withAllOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn("gemini-2.0-flash");
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(0.7);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(0.9);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(40);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(2048);

        try (MockedConstruction<Client> mocked = Mockito.mockConstruction(Client.class)) {
            ChatModel model = provider.createChatModel(instance, "valid-api-key");
            assertNotNull(model);
        }
    }

    @Test
    void testCreateChatModel_withInstanceValues() {
        instance.setModelName("gemini-1.5-flash");
        instance.setTemperature(0.5);
        instance.setTopP(0.8);
        instance.setTopK(20);

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        try (MockedConstruction<Client> mocked = Mockito.mockConstruction(Client.class)) {
            ChatModel model = provider.createChatModel(instance, "valid-api-key");
            assertNotNull(model);
        }
    }

    @Test
    void testCreateChatModel_modelFromOptionsOverridesInstance() {
        instance.setModelName("instance-model");

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn("options-model");
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        try (MockedConstruction<Client> mocked = Mockito.mockConstruction(Client.class)) {
            ChatModel model = provider.createChatModel(instance, "valid-api-key");
            assertNotNull(model);
        }
    }

    @Test
    void testCreateChatModel_noModelConfigured_usesDefault() {
        instance.setModelName(null);

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        try (MockedConstruction<Client> mocked = Mockito.mockConstruction(Client.class)) {
            ChatModel model = provider.createChatModel(instance, "valid-api-key");
            assertNotNull(model);
        }
    }

    @Test
    void testFetchContextWindow_returnsEmpty() {
        // Default implementation should return empty
        var result = provider.fetchContextWindow(instance, "key");
        assertEquals(java.util.OptionalInt.empty(), result);
    }
}
