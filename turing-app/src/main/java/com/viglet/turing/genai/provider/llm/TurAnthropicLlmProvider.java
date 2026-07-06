package com.viglet.turing.genai.provider.llm;

import java.util.Map;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@Component
public class TurAnthropicLlmProvider implements TurGenAiLlmProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_CHAT_MODEL = "claude-sonnet-4-20250514";

    private final TurProviderOptionsParser optionsParser;

    public TurAnthropicLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "anthropic";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        // Current Claude models (Opus 4.7/4.8, Sonnet 5, Fable 5) 400 when sent
        // temperature/top_p/top_k. Build the model with sampling params, and an
        // equivalent one without, so TurSamplingParamFallbackChatModel can retry
        // transparently on the "`temperature` is deprecated for this model" 400.
        return new TurSamplingParamFallbackChatModel(
                buildChatModel(turLLMInstance, decryptedApiKey, true),
                buildChatModel(turLLMInstance, decryptedApiKey, false));
    }

    private ChatModel buildChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey,
            boolean includeSamplingParams) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        var anthropicClientBuilder = AnthropicOkHttpClient.builder()
                .apiKey(requireApiKey(decryptedApiKey, turLLMInstance))
                .maxRetries(2);

        String baseUrl = resolveBaseUrl(firstNonBlank(
                optionsParser.stringValue(options, "baseUrl"),
                turLLMInstance.getUrl()));
        if (!DEFAULT_BASE_URL.equals(baseUrl)) {
            anthropicClientBuilder.baseUrl(baseUrl);
        }

        AnthropicChatOptions.Builder optionsBuilder = AnthropicChatOptions.builder()
                .model(resolveChatModelName(firstNonBlank(
                        optionsParser.stringValue(options, "chatModel"),
                        optionsParser.stringValue(options, "model"),
                        turLLMInstance.getModelName())));

        if (includeSamplingParams) {
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
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        return AnthropicChatModel.builder()
                .anthropicClient(anthropicClientBuilder.build())
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        throw new UnsupportedOperationException(
                "Anthropic does not provide an embedding API. Use a different provider for embeddings.");
    }

    @Override
    public java.util.List<TurLlmModelOption> listModels(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = resolveBaseUrl(firstNonBlank(
                optionsParser.stringValue(options, "baseUrl"),
                turLLMInstance.getUrl()));
        return TurLlmModelListingSupport.anthropic(baseUrl, decryptedApiKey);
    }

    private String requireApiKey(String decryptedApiKey, TurLLMInstance turLLMInstance) {
        if (!StringUtils.hasText(decryptedApiKey)) {
            throw new IllegalStateException(
                    "Missing API key for Anthropic provider in LLM instance: " + turLLMInstance.getId());
        }
        return decryptedApiKey;
    }

    private String resolveBaseUrl(String configuredUrl) {
        return StringUtils.hasText(configuredUrl) ? configuredUrl : DEFAULT_BASE_URL;
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
