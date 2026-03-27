package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class TurAzureOpenAiLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurAzureOpenAiLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
        instance.setUrl("https://my-resource.openai.azure.com");
    }

    @Test
    void testGetPluginType() {
        assertEquals("azure-openai", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_missingApiKey() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, null));
    }

    @Test
    void testCreateEmbeddingModel_missingApiKey() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> provider.createEmbeddingModel(instance, ""));
    }

    @Test
    void testCreateChatModel_success() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("deploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        instance.setModelName("gpt-4o");
        ChatModel model = provider.createChatModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_withAllOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("deploymentName"))).thenReturn("my-deployment");
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn("https://custom.openai.azure.com");
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(0.7);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(0.9);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(42);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(4096);

        ChatModel model = provider.createChatModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_defaultModelNameWhenNoneConfigured() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("deploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        instance.setModelName(null);
        // Should use default "gpt-4o"
        ChatModel model = provider.createChatModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_optionsFromInstance() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("deploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("maxTokens"))).thenReturn(null);

        instance.setModelName("gpt-35-turbo");
        instance.setTemperature(0.5);
        instance.setTopP(0.8);
        instance.setSeed(100);

        ChatModel model = provider.createChatModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_missingEndpoint() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("deploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("chatModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);

        instance.setUrl(null);
        assertThrows(IllegalStateException.class, () -> provider.createChatModel(instance, "test-api-key"));
    }

    @Test
    void testCreateEmbeddingModel_success() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingDeploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);

        instance.setModelName("text-embedding-3-small");
        EmbeddingModel model = provider.createEmbeddingModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateEmbeddingModel_defaultModelName() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingDeploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);

        instance.setModelName(null);
        // Should use default "text-embedding-ada-002"
        EmbeddingModel model = provider.createEmbeddingModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateEmbeddingModel_withCustomDeployment() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingDeploymentName"))).thenReturn("my-embed-deployment");
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn("https://custom.openai.azure.com");

        EmbeddingModel model = provider.createEmbeddingModel(instance, "test-api-key");
        assertNotNull(model);
    }

    @Test
    void testCreateEmbeddingModel_missingEndpoint() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingDeploymentName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("endpoint"))).thenReturn(null);

        instance.setUrl(null);
        assertThrows(IllegalStateException.class, () -> provider.createEmbeddingModel(instance, "test-api-key"));
    }

    @Test
    void testFirstNonNull() throws Exception {
        var method = TurAzureOpenAiLlmProvider.class.getDeclaredMethod("firstNonNull", Object[].class);
        method.setAccessible(true);

        assertEquals(42, method.invoke(provider, (Object) new Object[]{null, 42, 99}));
        assertNull(method.invoke(provider, (Object) new Object[]{null, null}));
    }

    @Test
    void testFirstNonBlank() throws Exception {
        var method = TurAzureOpenAiLlmProvider.class.getDeclaredMethod("firstNonBlank", String[].class);
        method.setAccessible(true);

        assertEquals("hello", method.invoke(provider, (Object) new String[]{null, "", "hello"}));
        assertNull(method.invoke(provider, (Object) new String[]{null, ""}));
    }
}
