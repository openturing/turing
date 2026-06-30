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
class TurBedrockLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurBedrockLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
    }

    @Test
    void testGetPluginType() {
        assertEquals("bedrock", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_missingModelId() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, "secret"));
    }

    @Test
    void testCreateChatModel_withStaticCredentialsAndModel() {
        instance.setModelName("anthropic.claude-3-5-sonnet-20241022-v2:0");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("region"))).thenReturn("us-east-1");
        when(optionsParser.stringValue(any(), eq("accessKeyId"))).thenReturn("AKIA-test");
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createChatModel(instance, "secret-access-key"));
    }

    @Test
    void testCreateChatModel_fallsBackToDefaultCredentialsChain() {
        instance.setModelName("amazon.nova-pro-v1:0");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        // No accessKeyId and no secret → DefaultCredentialsProvider chain, no throw.
        assertNotNull(provider.createChatModel(instance, null));
    }

    @Test
    void testCreateEmbeddingModel_titan() {
        instance.setModelName("amazon.titan-embed-text-v2:0");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createEmbeddingModel(instance, "secret"));
    }

    @Test
    void testCreateEmbeddingModel_missingModelId() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createEmbeddingModel(instance, "secret"));
    }
}
