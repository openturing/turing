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
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@ExtendWith(MockitoExtension.class)
class TurOpenAiCompatibleLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurOpenAiCompatibleLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
    }

    @Test
    void testGetPluginType() {
        assertEquals("openai-compatible", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_missingBaseUrl() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        // No instance URL and no baseUrl option → must fail fast.
        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, "some-key"));
    }

    @Test
    void testCreateChatModel_buildsWithBaseUrlFromInstanceUrl() {
        instance.setUrl("https://api.deepseek.com/v1");
        instance.setModelName("deepseek-chat");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createChatModel(instance, "deepseek-key"));
    }

    @Test
    void testCreateChatModel_allowsBlankApiKeyForLocalEndpoint() {
        instance.setUrl("http://localhost:8000/v1");
        instance.setModelName("local-model");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        // A local vLLM/LM-Studio endpoint usually needs no key — must not throw.
        assertNotNull(provider.createChatModel(instance, null));
    }

    @Test
    void testCreateChatModel_baseUrlFromOptionTakesPrecedence() {
        instance.setUrl("http://ignored");
        when(optionsParser.parse(any())).thenReturn(Map.of("baseUrl", "https://api.groq.com/openai/v1"));
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("https://api.groq.com/openai/v1");
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn("llama-3.3-70b");
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createChatModel(instance, "groq-key"));
    }

    @Test
    void testCreateEmbeddingModel_supported() {
        instance.setUrl("https://api.together.xyz/v1");
        instance.setModelName("BAAI/bge-base-en-v1.5");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        // Unlike gemini-openai, the generic provider exposes embeddings.
        assertNotNull(provider.createEmbeddingModel(instance, "together-key"));
    }

    @Test
    void testCreateEmbeddingModel_missingBaseUrl() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createEmbeddingModel(instance, "key"));
    }
}
