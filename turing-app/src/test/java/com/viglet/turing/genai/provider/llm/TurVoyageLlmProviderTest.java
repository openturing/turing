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
class TurVoyageLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurVoyageLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
    }

    @Test
    void testGetPluginType() {
        assertEquals("voyage", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_unsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> provider.createChatModel(instance, "key"));
    }

    @Test
    void testCreateEmbeddingModel_missingApiKey() {
        assertThrows(IllegalStateException.class,
                () -> provider.createEmbeddingModel(instance, null));
    }

    @Test
    void testCreateEmbeddingModel_supported() {
        instance.setModelName("voyage-3-large");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        var model = provider.createEmbeddingModel(instance, "voyage-key");
        assertNotNull(model);
        // Text-only model name → not the multimodal model (T511).
        assertEquals(TurVoyageEmbeddingModel.class, model.getClass());
    }

    @Test
    void testCreateEmbeddingModel_multimodalByModelName() {
        instance.setModelName("voyage-multimodal-3");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        var model = provider.createEmbeddingModel(instance, "voyage-key");
        assertEquals(TurVoyageMultimodalEmbeddingModel.class, model.getClass());
        // The multimodal model advertises the image-embedding capability.
        assertEquals(true, ((TurMultimodalEmbeddingModel) model).supportsImageEmbedding());
    }

    @Test
    void testCreateEmbeddingModel_multimodalByOption() {
        instance.setModelName("voyage-3");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("multimodal"))).thenReturn(Boolean.TRUE);

        var model = provider.createEmbeddingModel(instance, "voyage-key");
        assertEquals(TurVoyageMultimodalEmbeddingModel.class, model.getClass());
    }

    @Test
    void testCreateEmbeddingModel_contextualByModelName() {
        instance.setModelName("voyage-context-3");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        var model = provider.createEmbeddingModel(instance, "voyage-key");
        assertEquals(TurVoyageContextualEmbeddingModel.class, model.getClass());
        assertEquals(true, ((TurContextualEmbeddingModel) model).supportsContextualChunks());
    }

    @Test
    void testCreateEmbeddingModel_contextualByOption() {
        instance.setModelName("voyage-3");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("multimodal"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("contextual"))).thenReturn(Boolean.TRUE);

        var model = provider.createEmbeddingModel(instance, "voyage-key");
        assertEquals(TurVoyageContextualEmbeddingModel.class, model.getClass());
    }
}
