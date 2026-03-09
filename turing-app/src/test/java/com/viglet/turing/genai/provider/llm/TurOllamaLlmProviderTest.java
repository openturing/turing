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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@ExtendWith(MockitoExtension.class)
class TurOllamaLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurOllamaLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
        instance.setModelName("test-model");
        instance.setUrl("http://localhost:11434");
    }

    @Test
    void testGetPluginType() {
        assertEquals("ollama", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_success() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(null);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(java.util.List.of());

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_missingModel() {
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, "dummy-key"));
    }

    @Test
    void testCreateEmbeddingModel_success() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);

        EmbeddingModel model = provider.createEmbeddingModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateEmbeddingModel_missingModel() {
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createEmbeddingModel(instance, "dummy-key"));
    }
}
