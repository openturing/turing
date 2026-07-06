package com.viglet.turing.genai.provider.llm;

import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@Component
public class TurOpenAiLlmProvider implements TurGenAiLlmProvider {

    // --- S1192: extracted duplicated literals ---
    private static final String BASE_URL = "baseUrl";
    private static final String MODEL = "model";


    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final TurProviderOptionsParser optionsParser;

    public TurOpenAiLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "openai";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        // OpenAI reasoning models (o1/o3/o4-*) 400 when sent temperature/top_p/seed.
        // Build with sampling params + an equivalent without, and let
        // TurSamplingParamFallbackChatModel retry transparently on the rejection.
        return new TurSamplingParamFallbackChatModel(
                buildChatModel(turLLMInstance, decryptedApiKey, true),
                buildChatModel(turLLMInstance, decryptedApiKey, false));
    }

    private ChatModel buildChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey,
            boolean includeSamplingParams) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = resolveBaseUrl(firstNonBlank(
                optionsParser.stringValue(options, BASE_URL),
                turLLMInstance.getUrl()));
        String apiKey = requireApiKey(decryptedApiKey, turLLMInstance);
        OpenAIClient openAiClient = OpenAIOkHttpClient.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAIClientAsync openAiClientAsync = OpenAIOkHttpClientAsync.builder().baseUrl(baseUrl).apiKey(apiKey).build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(resolveChatModelName(firstNonBlank(
                        optionsParser.stringValue(options, "chatModel"),
                        optionsParser.stringValue(options, MODEL),
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
            Integer seed = firstNonNull(optionsParser.intValue(options, "seed"), turLLMInstance.getSeed());
            if (seed != null) {
                optionsBuilder.seed(seed);
            }
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClientAsync)
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public ChatModel createStreamingChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        // T18 / §IV.1.b: Spring AI 2.0.0-M6's SDK-based OpenAiChatModel
        // buffers the streaming response (it wraps the SDK iterator in
        // Flux.fromStream, which drains the iterator before publishing).
        // Bypass it with a hand-rolled WebClient that POSTs stream:true
        // and parses SSE data: lines so tokens reach the SSE pipeline as
        // they arrive from OpenAI — bubble fills in incrementally.
        return new TurSamplingParamFallbackChatModel(
                buildStreamingChatModel(turLLMInstance, decryptedApiKey, true),
                buildStreamingChatModel(turLLMInstance, decryptedApiKey, false));
    }

    private ChatModel buildStreamingChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey,
            boolean includeSamplingParams) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = resolveBaseUrl(firstNonBlank(
                optionsParser.stringValue(options, BASE_URL),
                turLLMInstance.getUrl()));
        String apiKey = requireApiKey(decryptedApiKey, turLLMInstance);
        String modelName = resolveChatModelName(firstNonBlank(
                optionsParser.stringValue(options, "chatModel"),
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName()));

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelName);
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
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }
        return new TurOpenAiStreamingChatModel(baseUrl, apiKey, modelName, optionsBuilder.build());
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        OpenAIClient openAiClient = OpenAIOkHttpClient.builder()
                .baseUrl(resolveBaseUrl(firstNonBlank(
                        optionsParser.stringValue(options, BASE_URL),
                        turLLMInstance.getUrl())))
                .apiKey(requireApiKey(decryptedApiKey, turLLMInstance))
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(resolveEmbeddingModelName(firstNonBlank(
                        optionsParser.stringValue(options, "embeddingModel"),
                        optionsParser.stringValue(options, MODEL),
                        turLLMInstance.getModelName())))
                .build();

        return OpenAiEmbeddingModel.builder()
                .openAiClient(openAiClient)
                .metadataMode(MetadataMode.NONE)
                .options(embeddingOptions)
                .build();
    }

    @Override
    public java.util.List<TurLlmModelOption> listModels(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = resolveBaseUrl(firstNonBlank(
                optionsParser.stringValue(options, BASE_URL),
                turLLMInstance.getUrl()));
        return TurLlmModelListingSupport.openAiStyle(baseUrl, decryptedApiKey);
    }

    private String requireApiKey(String decryptedApiKey, TurLLMInstance turLLMInstance) {
        if (!StringUtils.hasText(decryptedApiKey)) {
            throw new IllegalStateException(
                    "Missing API key for OpenAI provider in LLM instance: " + turLLMInstance.getId());
        }
        return decryptedApiKey;
    }

    private String resolveBaseUrl(String configuredUrl) {
        return StringUtils.hasText(configuredUrl) ? configuredUrl : DEFAULT_BASE_URL;
    }

    private String resolveChatModelName(String configuredModel) {
        return StringUtils.hasText(configuredModel) ? configuredModel : DEFAULT_CHAT_MODEL;
    }

    private String resolveEmbeddingModelName(String configuredModel) {
        return StringUtils.hasText(configuredModel) ? configuredModel : DEFAULT_EMBEDDING_MODEL;
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
