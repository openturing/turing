package com.viglet.turing.genai.provider.llm;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        var httpClient = HttpClients.custom()
                .setRetryStrategy(new HttpRequestRetryStrategy() {
                    @Override
                    public boolean retryRequest(HttpRequest request, IOException exception,
                            int execCount, HttpContext context) {
                        return execCount <= 2; // retry up to 2 times on I/O errors
                    }

                    @Override
                    public boolean retryRequest(HttpResponse response, int execCount,
                            HttpContext context) {
                        return false; // never retry on HTTP errors (429, 500, etc.)
                    }

                    @Override
                    public TimeValue getRetryInterval(HttpResponse response, int execCount,
                            HttpContext context) {
                        return TimeValue.ofSeconds(1);
                    }

                    @Override
                    public TimeValue getRetryInterval(HttpRequest request, IOException exception,
                            int execCount, HttpContext context) {
                        return TimeValue.ofSeconds(1);
                    }
                })
                .build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        var restClientBuilder = RestClient.builder().requestFactory(requestFactory);

        AnthropicApi anthropicApi = AnthropicApi.builder()
                .baseUrl(resolveBaseUrl(firstNonBlank(
                        optionsParser.stringValue(options, "baseUrl"),
                        turLLMInstance.getUrl())))
                .apiKey(requireApiKey(decryptedApiKey, turLLMInstance))
                .restClientBuilder(restClientBuilder)
                .build();

        AnthropicChatOptions.Builder optionsBuilder = AnthropicChatOptions.builder()
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
            optionsBuilder.maxTokens(maxTokens);
        }

        return AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        throw new UnsupportedOperationException(
                "Anthropic does not provide an embedding API. Use a different provider for embeddings.");
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
