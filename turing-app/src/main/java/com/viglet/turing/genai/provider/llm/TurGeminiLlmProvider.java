package com.viglet.turing.genai.provider.llm;

import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiEmbeddingModel;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * Native Gemini provider using Google's GenAI SDK directly.
 * Requires an API key from ai.google.dev (Google AI Studio).
 * Uses the native Gemini API (not OpenAI-compatible layer) for full
 * multimodal support including images, audio, and video.
 */
@Component
public class TurGeminiLlmProvider implements TurGenAiLlmProvider {

    private static final String DEFAULT_CHAT_MODEL = "gemini-2.0-flash";

    private final TurProviderOptionsParser optionsParser;

    public TurGeminiLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "gemini";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        Client genAiClient = Client.builder()
                .apiKey(requireApiKey(decryptedApiKey, turLLMInstance))
                .build();

        GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
                .model(resolveChatModelName(firstNonBlank(
                        optionsParser.stringValue(options, "chatModel"),
                        optionsParser.stringValue(options, "model"),
                        turLLMInstance.getModelName())));

        Double temperature = firstNonNull(optionsParser.doubleValue(options, "temperature"),
                turLLMInstance.getTemperature());
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Double topP = firstNonNull(optionsParser.doubleValue(options, "topP"), turLLMInstance.getTopP());
        if (topP != null) {
            optionsBuilder.topP(topP);
        }
        Integer topK = firstNonNull(optionsParser.intValue(options, "topK"), turLLMInstance.getTopK());
        if (topK != null) {
            optionsBuilder.topK(topK);
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxOutputTokens(maxTokens);
        }

        return GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        // T495 / §X.19 — native Gemini embeddings via embedContent, with
        // asymmetric task types (RETRIEVAL_DOCUMENT at index / RETRIEVAL_QUERY at
        // query) and optional Matryoshka outputDimensionality.
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        Client genAiClient = Client.builder()
                .apiKey(requireApiKey(decryptedApiKey, turLLMInstance))
                .build();

        String modelName = firstNonBlank(
                optionsParser.stringValue(options, "embeddingModel"),
                optionsParser.stringValue(options, "model"),
                turLLMInstance.getModelName());
        Integer outputDimensionality = optionsParser.intValue(options, "outputDimensionality");

        return new TurGeminiEmbeddingModel(genAiClient, modelName, outputDimensionality);
    }

    @Override
    public java.util.List<TurLlmModelOption> listModels(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        // Gemini native uses the GenAI Client (api key only); the REST list
        // endpoint lives under the generativelanguage v1beta base. Honour an
        // explicit baseUrl override if one was set in provider options / url.
        String baseUrl = firstNonBlank(
                optionsParser.stringValue(options, "baseUrl"),
                turLLMInstance.getUrl());
        return TurLlmModelListingSupport.gemini(baseUrl, decryptedApiKey);
    }

    private String requireApiKey(String decryptedApiKey, TurLLMInstance turLLMInstance) {
        if (!StringUtils.hasText(decryptedApiKey)) {
            throw new IllegalStateException(
                    "Missing API key for Gemini provider in LLM instance: " + turLLMInstance.getId());
        }
        return decryptedApiKey;
    }

    private String resolveChatModelName(String configuredModel) {
        return StringUtils.hasText(configuredModel) ? configuredModel : DEFAULT_CHAT_MODEL;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
