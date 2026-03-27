package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

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

/**
 * Tests for TurOllamaLlmProvider.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
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
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of());

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
    void testCreateChatModel_withAllOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn("llama3");
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://custom:11434");
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(0.7);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(40);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(0.9);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(1.1);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(42);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(256);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of("<|end|>", "<|stop|>"));

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_optionsFromInstance() {
        instance.setTemperature(0.5);
        instance.setTopK(20);
        instance.setTopP(0.8);
        instance.setRepeatPenalty(1.2);
        instance.setSeed(100);
        instance.setNumPredict(512);
        instance.setStop("stop1,stop2");

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(null);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of());

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_modelNameFromOptions() {
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn("options-model");
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(null);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of());

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
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

    @Test
    void testCreateEmbeddingModel_withEmbeddingModelFromOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn("nomic-embed-text");
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://custom:11434");

        EmbeddingModel model = provider.createEmbeddingModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateEmbeddingModel_modelFallbackFromOptionsModel() {
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("embeddingModel"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn("llama3");
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);

        EmbeddingModel model = provider.createEmbeddingModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testFetchContextWindow_missingBaseUrl() {
        instance.setUrl(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);

        OptionalInt result = provider.fetchContextWindow(instance, "dummy-key");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchContextWindow_missingModelName() {
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);

        OptionalInt result = provider.fetchContextWindow(instance, "dummy-key");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchContextWindow_bothMissing() {
        instance.setUrl(null);
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);

        OptionalInt result = provider.fetchContextWindow(instance, "dummy-key");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchContextWindow_connectionFailure() {
        // Using an invalid URL will cause a connection failure
        instance.setUrl("http://invalid-host-that-does-not-exist:99999");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);

        OptionalInt result = provider.fetchContextWindow(instance, "dummy-key");
        assertTrue(result.isEmpty());
    }

    @Test
    void testCreateChatModel_stopSequencesFromInstanceString() {
        instance.setStop("stop1, stop2, stop3");

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(null);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of());

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_emptyStopString() {
        instance.setStop("");

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(null);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of());

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testCreateChatModel_nullStopString() {
        instance.setStop(null);

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("temperature"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("topK"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("topP"))).thenReturn(null);
        when(optionsParser.doubleValue(any(), eq("repeatPenalty"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("seed"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("numPredict"))).thenReturn(null);
        when(optionsParser.stringListValue(any(), eq("stop"))).thenReturn(List.of());

        ChatModel model = provider.createChatModel(instance, "dummy-key");
        assertNotNull(model);
    }

    @Test
    void testParseStopSequences() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("parseStopSequences", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(provider, "stop1, stop2, stop3");
        assertEquals(3, result.size());
        assertEquals("stop1", result.get(0));
        assertEquals("stop2", result.get(1));
        assertEquals("stop3", result.get(2));
    }

    @Test
    void testParseStopSequences_empty() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("parseStopSequences", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(provider, "");
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseStopSequences_null() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("parseStopSequences", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(provider, (String) null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseStopSequences_withBlankEntries() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("parseStopSequences", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(provider, "a, , b, , c");
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
        assertEquals("c", result.get(2));
    }

    @Test
    void testExtractContextLength_exactMatch() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("extractContextLength", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> model = Map.of("name", "llama3", "context_length", 4096);
        OptionalInt result = (OptionalInt) method.invoke(provider, model, "llama3");
        assertTrue(result.isPresent());
        assertEquals(4096, result.getAsInt());
    }

    @Test
    void testExtractContextLength_withTagSuffix() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("extractContextLength", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> model = Map.of("name", "llama3:8b", "context_length", 8192);
        OptionalInt result = (OptionalInt) method.invoke(provider, model, "llama3");
        assertTrue(result.isPresent());
        assertEquals(8192, result.getAsInt());
    }

    @Test
    void testExtractContextLength_matchByModelField() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("extractContextLength", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> model = Map.of("name", "other-name", "model", "llama3", "context_length", 2048);
        OptionalInt result = (OptionalInt) method.invoke(provider, model, "llama3");
        assertTrue(result.isPresent());
        assertEquals(2048, result.getAsInt());
    }

    @Test
    void testExtractContextLength_noMatch() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("extractContextLength", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> model = Map.of("name", "mistral", "context_length", 4096);
        OptionalInt result = (OptionalInt) method.invoke(provider, model, "llama3");
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractContextLength_zeroContextLength() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("extractContextLength", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> model = Map.of("name", "llama3", "context_length", 0);
        OptionalInt result = (OptionalInt) method.invoke(provider, model, "llama3");
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractContextLength_nonNumberContextLength() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("extractContextLength", Map.class, String.class);
        method.setAccessible(true);

        Map<String, Object> model = Map.of("name", "llama3", "context_length", "not-a-number");
        OptionalInt result = (OptionalInt) method.invoke(provider, model, "llama3");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFirstNonNull() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("firstNonNull", Object[].class);
        method.setAccessible(true);

        assertEquals(42, method.invoke(provider, (Object) new Object[]{null, 42, 99}));
        assertEquals(1, method.invoke(provider, (Object) new Object[]{1, 2, 3}));
        assertNull(method.invoke(provider, (Object) new Object[]{null, null, null}));
    }

    @Test
    void testFirstNonBlank() throws Exception {
        var method = TurOllamaLlmProvider.class.getDeclaredMethod("firstNonBlank", String[].class);
        method.setAccessible(true);

        assertEquals("hello", method.invoke(provider, (Object) new String[]{null, "", "hello"}));
        assertEquals("first", method.invoke(provider, (Object) new String[]{"first", "second"}));
        assertNull(method.invoke(provider, (Object) new String[]{null, "", "  "}));
    }

    @Test
    void testFetchContextWindow_urlFromOptions() {
        instance.setUrl(null);
        instance.setModelName(null);
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid-host-99999:99999");
        when(optionsParser.stringValue(any(), eq("model"))).thenReturn("llama3");

        // URL is valid but unreachable, should fall through to empty
        OptionalInt result = provider.fetchContextWindow(instance, "dummy-key");
        assertTrue(result.isEmpty());
    }
}
