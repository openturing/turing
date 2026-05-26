package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
class TurEmbeddedLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurEmbeddedLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("embedded-id");
    }

    @Test
    void pluginTypeIsEmbeddedJlama() {
        assertEquals("embedded-jlama", provider.getPluginType());
    }

    @Test
    void readConfigParsesAllOptions() {
        Map<String, Object> options = Map.of();
        when(optionsParser.parse(any())).thenReturn(options);
        when(optionsParser.stringValue(eq(options), eq("modelAssetKey"))).thenReturn("models/llama3-8b");
        when(optionsParser.stringValue(eq(options), eq("role"))).thenReturn("chat");
        when(optionsParser.stringValue(eq(options), eq("workingQuant"))).thenReturn("F16");
        when(optionsParser.stringValue(eq(options), eq("modelQuant"))).thenReturn("I8");
        when(optionsParser.intValue(eq(options), eq("contextLength"))).thenReturn(8192);

        TurEmbeddedLlmProvider.EmbeddedConfig config = provider.readConfig(instance);

        assertEquals("models/llama3-8b", config.modelAssetKey());
        assertEquals("chat", config.role());
        assertEquals("F16", config.workingQuant());
        assertEquals("I8", config.modelQuant());
        assertEquals(8192, config.contextLength());
    }

    @Test
    void readConfigAppliesQuantizationDefaults() {
        Map<String, Object> options = Map.of();
        when(optionsParser.parse(any())).thenReturn(options);
        when(optionsParser.stringValue(eq(options), eq("modelAssetKey"))).thenReturn("models/bge-small");
        when(optionsParser.stringValue(eq(options), eq("role"))).thenReturn("embedding");
        when(optionsParser.stringValue(eq(options), eq("workingQuant"))).thenReturn(null);
        when(optionsParser.stringValue(eq(options), eq("modelQuant"))).thenReturn(null);
        when(optionsParser.intValue(eq(options), eq("contextLength"))).thenReturn(null);

        TurEmbeddedLlmProvider.EmbeddedConfig config = provider.readConfig(instance);

        assertEquals("F32", config.workingQuant());
        assertEquals("I8", config.modelQuant());
        assertNull(config.contextLength());
    }

    @Test
    void readConfigFallsBackToInstanceContextWindow() {
        instance.setContextWindow(4096);
        Map<String, Object> options = Map.of();
        when(optionsParser.parse(any())).thenReturn(options);
        when(optionsParser.stringValue(eq(options), eq("modelAssetKey"))).thenReturn("models/x");
        when(optionsParser.stringValue(eq(options), eq("role"))).thenReturn("chat");
        when(optionsParser.stringValue(eq(options), eq("workingQuant"))).thenReturn(null);
        when(optionsParser.stringValue(eq(options), eq("modelQuant"))).thenReturn(null);
        when(optionsParser.intValue(eq(options), eq("contextLength"))).thenReturn(null);

        TurEmbeddedLlmProvider.EmbeddedConfig config = provider.readConfig(instance);

        assertEquals(4096, config.contextLength());
    }

    @Test
    void readConfigRejectsMissingModelAssetKey() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        lenient().when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> provider.readConfig(instance));
        assertTrue(ex.getMessage().contains("modelAssetKey"));
        assertTrue(ex.getMessage().contains("embedded-id"));
    }

    @Test
    void readConfigRejectsInvalidRole() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn("models/x");
        when(optionsParser.stringValue(any(), eq("role"))).thenReturn("rerank");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> provider.readConfig(instance));
        assertTrue(ex.getMessage().contains("role"));
    }

    @Test
    void readConfigRejectsMissingRole() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn("models/x");
        when(optionsParser.stringValue(any(), eq("role"))).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> provider.readConfig(instance));
        assertTrue(ex.getMessage().contains("role"));
    }

    @Test
    void createChatModelRejectsEmbeddingRole() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn("models/bge-small");
        when(optionsParser.stringValue(any(), eq("role"))).thenReturn("embedding");
        when(optionsParser.stringValue(any(), eq("workingQuant"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("modelQuant"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("contextLength"))).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.createChatModel(instance, null));
        assertTrue(ex.getMessage().contains("'chat'"));
        assertTrue(ex.getMessage().contains("'embedding'"));
    }

    @Test
    void createEmbeddingModelRejectsChatRole() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn("models/llama3-8b");
        when(optionsParser.stringValue(any(), eq("role"))).thenReturn("chat");
        when(optionsParser.stringValue(any(), eq("workingQuant"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("modelQuant"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("contextLength"))).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.createEmbeddingModel(instance, null));
        assertTrue(ex.getMessage().contains("'embedding'"));
        assertTrue(ex.getMessage().contains("'chat'"));
    }

    @Test
    void createChatModelPendingT194T197() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn("models/llama3-8b");
        when(optionsParser.stringValue(any(), eq("role"))).thenReturn("chat");
        when(optionsParser.stringValue(any(), eq("workingQuant"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("modelQuant"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("contextLength"))).thenReturn(null);

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> provider.createChatModel(instance, null));
        assertTrue(ex.getMessage().contains("T194"));
        assertTrue(ex.getMessage().contains("T197"));
        assertTrue(ex.getMessage().contains("embedded-id"));
    }

    @Test
    void createEmbeddingModelPendingT194T198() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("modelAssetKey"))).thenReturn("models/bge-small");
        when(optionsParser.stringValue(any(), eq("role"))).thenReturn("embedding");
        when(optionsParser.stringValue(any(), eq("workingQuant"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("modelQuant"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("contextLength"))).thenReturn(null);

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> provider.createEmbeddingModel(instance, null));
        assertTrue(ex.getMessage().contains("T194"));
        assertTrue(ex.getMessage().contains("T198"));
    }
}
